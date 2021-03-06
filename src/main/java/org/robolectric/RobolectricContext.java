package org.robolectric;

import org.apache.maven.artifact.ant.DependenciesTask;
import org.apache.maven.model.Dependency;
import org.apache.tools.ant.Project;
import org.robolectric.bytecode.AndroidTranslator;
import org.robolectric.bytecode.AsmInstrumentingClassLoader;
import org.robolectric.bytecode.ClassCache;
import org.robolectric.bytecode.ClassHandler;
import org.robolectric.bytecode.JavassistInstrumentingClassLoader;
import org.robolectric.bytecode.RobolectricInternals;
import org.robolectric.bytecode.Setup;
import org.robolectric.bytecode.ShadowWrangler;
import org.robolectric.bytecode.ZipClassCache;
import org.robolectric.internal.RobolectricTestRunnerInterface;
import org.robolectric.res.AndroidResourcePathFinder;
import org.robolectric.res.ResourcePath;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.robolectric.RobolectricTestRunner.isBootstrapped;

public class RobolectricContext {
    private static final Map<Class<? extends RobolectricTestRunner>, RobolectricContext> contextsByTestRunner = new HashMap<Class<? extends RobolectricTestRunner>, RobolectricContext>();

    private final AndroidManifest appManifest;
    private final ClassLoader robolectricClassLoader;
    private final ClassHandler classHandler;
    public static RobolectricContext mostRecentRobolectricContext; // ick, race condition

    public interface Factory {
        RobolectricContext create();
    }

    public static Class<?> bootstrap(Class<? extends RobolectricTestRunner> robolectricTestRunnerClass, Class<?> testClass, Factory factory) {
        if (isBootstrapped(robolectricTestRunnerClass) || isBootstrapped(testClass)) {
            if (!isBootstrapped(testClass)) throw new IllegalStateException("test class is somehow not bootstrapped");
            return testClass;
        }

        RobolectricContext robolectricContext;
        synchronized (contextsByTestRunner) {
            robolectricContext = contextsByTestRunner.get(robolectricTestRunnerClass);
            if (robolectricContext == null) {
                try {
                    robolectricContext = factory.create();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                contextsByTestRunner.put(robolectricTestRunnerClass, robolectricContext);
            }
        }

        mostRecentRobolectricContext = robolectricContext;

        return robolectricContext.bootstrapTestClass(testClass);
    }

    public RobolectricContext() {
        Setup setup = createSetup();
        classHandler = createClassHandler(setup);
        appManifest = createAppManifest();
        robolectricClassLoader = createRobolectricClassLoader(setup);
    }

    private ClassHandler createClassHandler(Setup setup) {
        return new ShadowWrangler(setup);
    }

    public ClassCache createClassCache() {
        final String classCachePath = System.getProperty("cached.robolectric.classes.path");
        final File classCacheDirectory;
        if (null == classCachePath || "".equals(classCachePath.trim())) {
            classCacheDirectory = new File("./tmp");
        } else {
            classCacheDirectory = new File(classCachePath);
        }

        return new ZipClassCache(new File(classCacheDirectory, "cached-robolectric-classes.jar").getAbsolutePath(), AndroidTranslator.CACHE_VERSION);
    }

    public AndroidTranslator createAndroidTranslator(Setup setup, ClassCache classCache) {
        return new AndroidTranslator(classCache, setup);
    }

    protected AndroidManifest createAppManifest() {
        return new AndroidManifest(new File("."));
    }

    public AndroidManifest getAppManifest() {
        return appManifest;
    }

    public ClassHandler getClassHandler() {
        return classHandler;
    }

    public ResourcePath getSystemResourcePath() {
        AndroidManifest manifest = getAppManifest();
        return AndroidResourcePathFinder.getSystemResourcePath(manifest.getRealSdkVersion(), manifest.getResourcePath());
    }

    private Class<?> bootstrapTestClass(Class<?> testClass) {
        try {
            return robolectricClassLoader.loadClass(testClass.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public RobolectricTestRunnerInterface getBootstrappedTestRunner(RobolectricTestRunnerInterface originalTestRunner) {
        Class<?> originalTestClass = originalTestRunner.getTestClass().getJavaClass();
        Class<?> bootstrappedTestClass = bootstrapTestClass(originalTestClass);
        Class<?> bootstrappedTestRunnerClass = bootstrapTestClass(originalTestRunner.getClass());

        try {
            Constructor<?> constructorForDelegate = bootstrappedTestRunnerClass.getConstructor(Class.class);
            return (RobolectricTestRunnerInterface) constructorForDelegate.newInstance(bootstrappedTestClass);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setRobolectricContextField(Class<?> testRunnerClass) {
        Class<?> clazz = testRunnerClass;
        while (!clazz.getName().equals(RobolectricTestRunner.class.getName())) {
            clazz = clazz.getSuperclass();
            if (clazz == null)
                throw new RuntimeException(testRunnerClass + " doesn't extend RobolectricTestRunner");
        }
        try {
            Field field = clazz.getDeclaredField("sharedRobolectricContext");
            field.setAccessible(true);
            field.set(null, this);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected ClassLoader createRobolectricClassLoader(Setup setup) {
        URL[] urls = artifactUrls(realAndroidDependency("android-base"),
                realAndroidDependency("android-kxml2"),
                realAndroidDependency("android-luni"),
                createDependency("org.json", "json", "20080701", "jar", null)
        );
        ClassLoader robolectricClassLoader;
        if (useAsm()) {
            robolectricClassLoader = new AsmInstrumentingClassLoader(setup, urls);
        } else {
            ClassCache classCache = createClassCache();
            AndroidTranslator androidTranslator = createAndroidTranslator(setup, classCache);
            ClassLoader realSdkClassLoader = JavassistInstrumentingClassLoader.makeClassloader(this.getClass().getClassLoader(), urls);
            robolectricClassLoader = new JavassistInstrumentingClassLoader(realSdkClassLoader, classCache, androidTranslator, setup);
        }
        injectClassHandler(robolectricClassLoader);
        return robolectricClassLoader;
    }

    public boolean useAsm() {
        return true;
    }

    private void injectClassHandler(ClassLoader robolectricClassLoader) {
        try {
            String className = RobolectricInternals.class.getName();
            Class<?> robolectricInternalsClass = robolectricClassLoader.loadClass(className);
            Field field = robolectricInternalsClass.getDeclaredField("classHandler");
            field.setAccessible(true);
            field.set(null, classHandler);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public ClassLoader getRobolectricClassLoader() {
        return robolectricClassLoader;
    }

    public Setup createSetup() {
        return new Setup();
    }

    private URL[] artifactUrls(Dependency... dependencies) {
        DependenciesTask dependenciesTask = new DependenciesTask();
        configureMaven(dependenciesTask);
        Project project = new Project();
        dependenciesTask.setProject(project);
        for (Dependency dependency : dependencies) {
            dependenciesTask.addDependency(dependency);
        }
        dependenciesTask.execute();

        @SuppressWarnings("unchecked")
        Hashtable<String, String> artifacts = project.getProperties();
        URL[] urls = new URL[artifacts.size()];
        int i = 0;
        for (String path : artifacts.values()) {
            try {
                urls[i++] = new URL("file://" + path);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        return urls;
    }

    @SuppressWarnings("UnusedParameters")
    protected void configureMaven(DependenciesTask dependenciesTask) {
        // maybe you want to override this method and some settings?
    }

    private Dependency realAndroidDependency(String artifactId) {
        return createDependency("org.robolectric", artifactId, "4.1.2_r1_rc", "jar", "real");
    }

    private Dependency createDependency(String groupId, String artifactId, String version, String type, String classifier) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        dependency.setType(type);
        dependency.setClassifier(classifier);
        return dependency;
    }

    /**
     * @deprecated use {@link org.robolectric.Robolectric.Reflection#setFinalStaticField(Class, String, Object)}
     */
    public static void setStaticValue(Class<?> clazz, String fieldName, Object value) {
        Robolectric.Reflection.setFinalStaticField(clazz, fieldName, value);
    }
}
