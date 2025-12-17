/******************************************************************************
 *  Copyright 2015 by OLTPBenchmark Project                                   *
 *                                                                            *
 *  Licensed under the Apache License, Version 2.0 (the "License");           *
 *  you may not use this file except in compliance with the License.          *
 *  You may obtain a copy of the License at                                   *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 *  Unless required by applicable law or agreed to in writing, software       *
 *  distributed under the License is distributed on an "AS IS" BASIS,         *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 *  See the License for the specific language governing permissions and       *
 *  limitations under the License.                                            *
 ******************************************************************************/

package com.oltpbenchmark.api;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oltpbenchmark.types.TransactionStatus;

/**
 * High-performance SQLException handler using lookup tables instead of if-else chains.
 * This provides O(1) lookup for known error codes instead of O(n) sequential checks.
 */
public class SQLExceptionHandler {

    /**
     * Key for the exception lookup table combining error code and SQL state
     */
    private static final class ExceptionKey {
        final int errorCode;
        final String sqlState;

        ExceptionKey(int errorCode, String sqlState) {
            this.errorCode = errorCode;
            this.sqlState = sqlState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExceptionKey that = (ExceptionKey) o;
            return errorCode == that.errorCode && Objects.equals(sqlState, that.sqlState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, sqlState);
        }
    }

    /**
     * Lookup table for known retryable exceptions
     */
    private static final Map<ExceptionKey, TransactionStatus> EXCEPTION_MAP = new HashMap<>();

    /**
     * Lookup table for SQL state only (when error code is 0 or varies)
     */
    private static final Map<String, TransactionStatus> SQL_STATE_MAP = new HashMap<>();

    /**
     * Set of SQL states that should throw exceptions (non-retryable errors)
     */
    private static final Map<String, Boolean> THROW_EXCEPTION_STATES = new HashMap<>();

    static {
        // ------------------
        // MYSQL
        // ------------------
        // MySQLTransactionRollbackException - Deadlock
        EXCEPTION_MAP.put(new ExceptionKey(1213, "40001"), TransactionStatus.RETRY);
        // MySQL Lock timeout
        EXCEPTION_MAP.put(new ExceptionKey(1205, "41000"), TransactionStatus.RETRY);

        // ------------------
        // SQL SERVER
        // ------------------
        // SQLServerException Deadlock
        EXCEPTION_MAP.put(new ExceptionKey(1205, "40001"), TransactionStatus.RETRY);

        // ------------------
        // ORACLE
        // ------------------
        // ORA-08177: Oracle Serialization
        EXCEPTION_MAP.put(new ExceptionKey(8177, "72000"), TransactionStatus.RETRY);

        // ------------------
        // DB2
        // ------------------
        // DB2Exception Deadlock
        EXCEPTION_MAP.put(new ExceptionKey(-911, "40001"), TransactionStatus.RETRY);
        // Query cancelled - DB2
        EXCEPTION_MAP.put(new ExceptionKey(0, "57014"), TransactionStatus.RETRY_DIFFERENT);
        EXCEPTION_MAP.put(new ExceptionKey(-952, "57014"), TransactionStatus.RETRY_DIFFERENT);

        // ------------------
        // POSTGRES (error code is typically 0)
        // ------------------
        // Postgres serialization
        SQL_STATE_MAP.put("40001", TransactionStatus.RETRY);
        // Postgres OOM error - should throw
        THROW_EXCEPTION_STATES.put("53200", true);
        // Postgres no pinned buffers available - should throw
        THROW_EXCEPTION_STATES.put("XX000", true);

        // ------------------
        // GENERAL
        // ------------------
        // No results returned - retry different
        SQL_STATE_MAP.put("02000", TransactionStatus.RETRY_DIFFERENT);
    }

    /**
     * Determine how to handle a SQLException.
     *
     * @param ex The SQLException to handle
     * @return The appropriate TransactionStatus, or null if the exception should be thrown
     */
    public static TransactionStatus handleException(SQLException ex) {
        if (ex == null || ex.getSQLState() == null) {
            return TransactionStatus.RETRY; // Default to retry for unknown
        }

        int errorCode = ex.getErrorCode();
        String sqlState = ex.getSQLState();

        // Check if this should throw an exception
        if (THROW_EXCEPTION_STATES.containsKey(sqlState)) {
            return null; // Signal to throw the exception
        }

        // First try exact match with error code and SQL state
        ExceptionKey key = new ExceptionKey(errorCode, sqlState);
        TransactionStatus status = EXCEPTION_MAP.get(key);
        if (status != null) {
            return status;
        }

        // Then try SQL state only (for databases where error code is 0 or varies)
        status = SQL_STATE_MAP.get(sqlState);
        if (status != null) {
            return status;
        }

        // Default: retry for unknown errors (maintains backward compatibility)
        return TransactionStatus.RETRY;
    }

    /**
     * Check if the exception should cause a retry
     * @param ex The SQLException to check
     * @return true if should retry, false otherwise
     */
    public static boolean shouldRetry(SQLException ex) {
        TransactionStatus status = handleException(ex);
        return status == TransactionStatus.RETRY || status == TransactionStatus.RETRY_DIFFERENT;
    }

    /**
     * Check if the exception should be thrown (non-recoverable error)
     * @param ex The SQLException to check
     * @return true if should throw, false otherwise
     */
    public static boolean shouldThrow(SQLException ex) {
        return handleException(ex) == null;
    }
}
