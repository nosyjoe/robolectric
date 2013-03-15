package org.robolectric.shadows;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.CancellationSignal;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import org.robolectric.internal.Implementation;
import org.robolectric.internal.Implements;
import org.robolectric.util.Join;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shadow for {@code SQLiteQueryBuilder}.
 */
@Implements(SQLiteQueryBuilder.class)
public class ShadowSQLiteQueryBuilder {

    private static final String TAG = "SQLiteQueryBuilder";
    private static final Pattern sLimitPattern =
            Pattern.compile("\\s*\\d+\\s*(,\\s*\\d+\\s*)?");

    private Map<String, String> mProjectionMap = null;
    private String mTables = "";
    private StringBuilder mWhereClause = null;  // lazily created
    private boolean mDistinct;
    private SQLiteDatabase.CursorFactory mFactory;
    private boolean mStrict;


    @Implementation
    public static String buildQueryString(boolean distinct, String tables,
                                          String[] columns, String where, String groupBy, String having,
                                          String orderBy, String limit) {

        StringBuilder sb = new StringBuilder("SELECT ");

        if (distinct) {
            sb.append("DISTINCT ");
        }

        if (columns != null) {
            sb.append(Join.join(", ", (Object[]) columns));
        } else {
            sb.append("*");
        }

        sb.append(" FROM ");
        sb.append(tables);

        conditionallyAppend(sb, " WHERE ", where);
        conditionallyAppend(sb, " GROUP BY ", groupBy);
        conditionallyAppend(sb, " HAVING ", having);
        conditionallyAppend(sb, " ORDER BY ", orderBy);
        conditionallyAppend(sb, " LIMIT ", limit);

        return sb.toString();
    }

    @Implementation
    public Cursor query(SQLiteDatabase db, String[] projectionIn,
                        String selection, String[] selectionArgs, String groupBy,
                        String having, String sortOrder) {
        return query(db, projectionIn, selection, selectionArgs, groupBy, having, sortOrder,
                null /* limit */, null /* cancellationSignal */);
    }

    @Implementation
    public Cursor query(SQLiteDatabase db, String[] projectionIn,
                        String selection, String[] selectionArgs, String groupBy,
                        String having, String sortOrder, String limit) {
        return query(db, projectionIn, selection, selectionArgs,
                groupBy, having, sortOrder, limit, null);
    }

    @Implementation
    public Cursor query(SQLiteDatabase db, String[] projectionIn,
                        String selection, String[] selectionArgs, String groupBy,
                        String having, String sortOrder, String limit, CancellationSignal cancellationSignal) {
        if (mTables == null) {
            return null;
        }

        if (mStrict && selection != null && selection.length() > 0) {
            // Validate the user-supplied selection to detect syntactic anomalies
            // in the selection string that could indicate a SQL injection attempt.
            // The idea is to ensure that the selection clause is a valid SQL expression
            // by compiling it twice: once wrapped in parentheses and once as
            // originally specified. An attacker cannot create an expression that
            // would escape the SQL expression while maintaining balanced parentheses
            // in both the wrapped and original forms.
            String sqlForValidation = buildQuery(projectionIn, "(" + selection + ")", groupBy,
                    having, sortOrder, limit);
            validateQuerySql(db, sqlForValidation,
                    cancellationSignal); // will throw if query is invalid
        }

        String sql = buildQuery(
                projectionIn, selection, groupBy, having,
                sortOrder, limit);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Performing query: " + sql);
        }
        return db.rawQueryWithFactory(
                mFactory, sql, selectionArgs,
                SQLiteDatabase.findEditTable(mTables),
                cancellationSignal); // will throw if query is invalid
    }

    private void validateQuerySql(SQLiteDatabase db, String sql,
                                  CancellationSignal cancellationSignal) {
//        db.getThreadSession().prepare(sql,
//                db.getThreadDefaultConnectionFlags(true /*readOnly*/), cancellationSignal, null);
    }

    @Implementation
    public void setCursorFactory(SQLiteDatabase.CursorFactory factory) {
        mFactory = factory;
    }


    @Implementation
    public String buildQuery(
            String[] projectionIn, String selection, String groupBy,
            String having, String sortOrder, String limit) {
        String[] projection = computeProjection(projectionIn);

        StringBuilder where = new StringBuilder();
        boolean hasBaseWhereClause = mWhereClause != null && mWhereClause.length() > 0;

        if (hasBaseWhereClause) {
            where.append(mWhereClause.toString());
            where.append(')');
        }

        // Tack on the user's selection, if present.
        if (selection != null && selection.length() > 0) {
            if (hasBaseWhereClause) {
                where.append(" AND ");
            }

            where.append('(');
            where.append(selection);
            where.append(')');
        }

        return buildQueryString(
                mDistinct, mTables, projection, where.toString(),
                groupBy, having, sortOrder, limit);
    }

    @Implementation
    public void setTables(String inTables) {
        mTables = inTables;
    }

    @Implementation
    public void setProjectionMap(Map<String, String> columnMap) {
        mProjectionMap = columnMap;
    }

    private String[] computeProjection(String[] projectionIn) {
        if (projectionIn != null && projectionIn.length > 0) {
            if (mProjectionMap != null) {
                String[] projection = new String[projectionIn.length];
                int length = projectionIn.length;

                for (int i = 0; i < length; i++) {
                    String userColumn = projectionIn[i];
                    String column = mProjectionMap.get(userColumn);

                    if (column != null) {
                        projection[i] = column;
                        continue;
                    }

                    if (!mStrict &&
                            ( userColumn.contains(" AS ") || userColumn.contains(" as "))) {
                        /* A column alias already exist */
                        projection[i] = userColumn;
                        continue;
                    }

                    throw new IllegalArgumentException("Invalid column "
                            + projectionIn[i]);
                }
                return projection;
            } else {
                return projectionIn;
            }
        } else if (mProjectionMap != null) {
            // Return all columns in projection map.
            Set<Map.Entry<String, String>> entrySet = mProjectionMap.entrySet();
            String[] projection = new String[entrySet.size()];
            Iterator<Map.Entry<String, String>> entryIter = entrySet.iterator();
            int i = 0;

            while (entryIter.hasNext()) {
                Map.Entry<String, String> entry = entryIter.next();

                // Don't include the _count column when people ask for no projection.
                if (entry.getKey().equals(BaseColumns._COUNT)) {
                    continue;
                }
                projection[i++] = entry.getValue();
            }
            return projection;
        }
        return null;
    }

    private static void conditionallyAppend(StringBuilder sb, String keyword, String value) {
        if (!TextUtils.isEmpty(value)) {
            sb.append(keyword);
            sb.append(value);
        }
    }

}
