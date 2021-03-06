/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.analysis.analyzer;

import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.sql.analysis.AnalysisException;
import org.elasticsearch.xpack.sql.analysis.index.EsIndex;
import org.elasticsearch.xpack.sql.analysis.index.IndexResolution;
import org.elasticsearch.xpack.sql.expression.function.FunctionRegistry;
import org.elasticsearch.xpack.sql.parser.SqlParser;
import org.elasticsearch.xpack.sql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.sql.type.EsField;
import org.elasticsearch.xpack.sql.type.TypesTests;

import java.util.Map;
import java.util.TimeZone;

public class VerifierErrorMessagesTests extends ESTestCase {
    private SqlParser parser = new SqlParser();

    private String verify(String sql) {
        Map<String, EsField> mapping = TypesTests.loadMapping("mapping-multi-field-with-nested.json");
        EsIndex test = new EsIndex("test", mapping);
        return verify(IndexResolution.valid(test), sql);
    }

    private String verify(IndexResolution getIndexResult, String sql) {
        Analyzer analyzer = new Analyzer(new FunctionRegistry(), getIndexResult, TimeZone.getTimeZone("UTC"));
        AnalysisException e = expectThrows(AnalysisException.class, () -> analyzer.analyze(parser.createStatement(sql), true));
        assertTrue(e.getMessage().startsWith("Found "));
        String header = "Found 1 problem(s)\nline ";
        return e.getMessage().substring(header.length());
    }

    private LogicalPlan accepted(String sql) {
        Map<String, EsField> mapping = TypesTests.loadMapping("mapping-multi-field-with-nested.json");
        EsIndex test = new EsIndex("test", mapping);
        Analyzer analyzer = new Analyzer(new FunctionRegistry(), IndexResolution.valid(test), TimeZone.getTimeZone("UTC"));
        return analyzer.analyze(parser.createStatement(sql), true);
    }

    public void testMissingIndex() {
        assertEquals("1:17: Unknown index [missing]", verify(IndexResolution.notFound("missing"), "SELECT foo FROM missing"));
    }

    public void testMissingColumn() {
        assertEquals("1:8: Unknown column [xxx]", verify("SELECT xxx FROM test"));
    }
    
    public void testMissingColumnWithWildcard() {
        assertEquals("1:8: Unknown column [xxx]", verify("SELECT xxx.* FROM test"));
    }
    
    public void testMisspelledColumnWithWildcard() {
        assertEquals("1:8: Unknown column [tex], did you mean [text]?", verify("SELECT tex.* FROM test"));
    }
    
    public void testColumnWithNoSubFields() {
        assertEquals("1:8: Cannot determine columns for [text.*]", verify("SELECT text.* FROM test"));
    }
    
    public void testMultipleColumnsWithWildcard1() {
        assertEquals("1:14: Unknown column [a]\n" + 
                "line 1:17: Unknown column [b]\n" + 
                "line 1:22: Unknown column [c]\n" + 
                "line 1:25: Unknown column [tex], did you mean [text]?", verify("SELECT bool, a, b.*, c, tex.* FROM test"));
    }
    
    public void testMultipleColumnsWithWildcard2() {
        assertEquals("1:8: Unknown column [tex], did you mean [text]?\n" + 
                "line 1:21: Unknown column [a]\n" + 
                "line 1:24: Unknown column [dat], did you mean [date]?\n" + 
                "line 1:31: Unknown column [c]", verify("SELECT tex.*, bool, a, dat.*, c FROM test"));
    }
    
    public void testMultipleColumnsWithWildcard3() {
        assertEquals("1:8: Unknown column [ate], did you mean [date]?\n" + 
                "line 1:21: Unknown column [keyw], did you mean [keyword]?\n" + 
                "line 1:29: Unknown column [da], did you mean [date]?" , verify("SELECT ate.*, bool, keyw.*, da FROM test"));
    }

    public void testMisspelledColumn() {
        assertEquals("1:8: Unknown column [txt], did you mean [text]?", verify("SELECT txt FROM test"));
    }

    public void testFunctionOverMissingField() {
        assertEquals("1:12: Unknown column [xxx]", verify("SELECT ABS(xxx) FROM test"));
    }

    public void testMissingFunction() {
        assertEquals("1:8: Unknown function [ZAZ]", verify("SELECT ZAZ(bool) FROM test"));
    }

    public void testMisspelledFunction() {
        assertEquals("1:8: Unknown function [COONT], did you mean any of [COUNT, COT, CONCAT]?", verify("SELECT COONT(bool) FROM test"));
    }

    public void testMissingColumnInGroupBy() {
        assertEquals("1:41: Unknown column [xxx]", verify("SELECT * FROM test GROUP BY DAY_OF_YEAR(xxx)"));
    }

    public void testFilterOnUnknownColumn() {
        assertEquals("1:26: Unknown column [xxx]", verify("SELECT * FROM test WHERE xxx = 1"));
    }

    public void testMissingColumnInOrderBy() {
        // xxx offset is that of the order by field
        assertEquals("1:29: Unknown column [xxx]", verify("SELECT * FROM test ORDER BY xxx"));
    }

    public void testMissingColumnFunctionInOrderBy() {
        // xxx offset is that of the order by field
        assertEquals("1:41: Unknown column [xxx]", verify("SELECT * FROM test ORDER BY DAY_oF_YEAR(xxx)"));
    }

    public void testMissingExtract() {
        assertEquals("1:8: Unknown datetime field [ZAZ]", verify("SELECT EXTRACT(ZAZ FROM date) FROM test"));
    }

    public void testMissingExtractSimilar() {
        assertEquals("1:8: Unknown datetime field [DAP], did you mean [DAY]?", verify("SELECT EXTRACT(DAP FROM date) FROM test"));
    }

    public void testMissingExtractSimilarMany() {
        assertEquals("1:8: Unknown datetime field [DOP], did you mean any of [DOM, DOW, DOY]?",
            verify("SELECT EXTRACT(DOP FROM date) FROM test"));
    }

    public void testExtractNonDateTime() {
        assertEquals("1:8: Invalid datetime field [ABS]. Use any datetime function.", verify("SELECT EXTRACT(ABS FROM date) FROM test"));
    }

    public void testMultipleColumns() {
        // xxx offset is that of the order by field
        assertEquals("1:43: Unknown column [xxx]\nline 1:8: Unknown column [xxx]",
                verify("SELECT xxx FROM test GROUP BY DAY_oF_YEAR(xxx)"));
    }

    // GROUP BY
    public void testGroupBySelectNonGrouped() {
        assertEquals("1:8: Cannot use non-grouped column [text], expected [int]",
                verify("SELECT text, int FROM test GROUP BY int"));
    }

    public void testGroupByOrderByNonGrouped() {
        assertEquals("1:50: Cannot order by non-grouped column [bool], expected [text]",
                verify("SELECT MAX(int) FROM test GROUP BY text ORDER BY bool"));
    }

    public void testGroupByOrderByNonGrouped_WithHaving() {
        assertEquals("1:71: Cannot order by non-grouped column [bool], expected [text]",
            verify("SELECT MAX(int) FROM test GROUP BY text HAVING MAX(int) > 10 ORDER BY bool"));
    }

    public void testGroupByOrderByAliasedInSelectAllowed() {
        LogicalPlan lp = accepted("SELECT text t FROM test GROUP BY text ORDER BY t");
        assertNotNull(lp);
    }

    public void testGroupByOrderByScalarOverNonGrouped() {
        assertEquals("1:50: Cannot order by non-grouped column [YEAR(date [UTC])], expected [text]",
                verify("SELECT MAX(int) FROM test GROUP BY text ORDER BY YEAR(date)"));
    }

    public void testGroupByOrderByScalarOverNonGrouped_WithHaving() {
        assertEquals("1:71: Cannot order by non-grouped column [YEAR(date [UTC])], expected [text]",
            verify("SELECT MAX(int) FROM test GROUP BY text HAVING MAX(int) > 10 ORDER BY YEAR(date)"));
    }

    public void testGroupByHavingNonGrouped() {
        assertEquals("1:48: Cannot filter by non-grouped column [int], expected [text]",
                verify("SELECT AVG(int) FROM test GROUP BY text HAVING int > 10"));
    }

    public void testGroupByAggregate() {
        assertEquals("1:36: Cannot use an aggregate [AVG] for grouping",
                verify("SELECT AVG(int) FROM test GROUP BY AVG(int)"));
    }

    public void testGroupByOnNested() {
        assertEquals("1:38: Grouping isn't (yet) compatible with nested fields [dep.dep_id]",
                verify("SELECT dep.dep_id FROM test GROUP BY dep.dep_id"));
    }

    public void testHavingOnNested() {
        assertEquals("1:51: HAVING isn't (yet) compatible with nested fields [dep.start_date]",
                verify("SELECT int FROM test GROUP BY int HAVING AVG(YEAR(dep.start_date)) > 1980"));
    }

    public void testGroupByScalarFunctionWithAggOnTarget() {
        assertEquals("1:31: Cannot use an aggregate [AVG] for grouping",
                verify("SELECT int FROM test GROUP BY AVG(int) + 2"));
    }

    public void testUnsupportedType() {
        assertEquals("1:8: Cannot use field [unsupported] type [ip_range] as is unsupported",
                verify("SELECT unsupported FROM test"));
    }

    public void testGroupByOrderByNonKey() {
        assertEquals("1:52: Cannot order by non-grouped column [a], expected [bool]",
                verify("SELECT AVG(int) a FROM test GROUP BY bool ORDER BY a"));
    }

    public void testGroupByOrderByFunctionOverKey() {
        assertEquals("1:44: Cannot order by non-grouped column [MAX(int)], expected [int]",
                verify("SELECT int FROM test GROUP BY int ORDER BY MAX(int)"));
    }

    public void testGroupByOrderByScore() {
        assertEquals("1:44: Cannot order by non-grouped column [SCORE()], expected [int]",
                verify("SELECT int FROM test GROUP BY int ORDER BY SCORE()"));
    }

    public void testHavingOnColumn() {
        assertEquals("1:42: Cannot filter HAVING on non-aggregate [int]; consider using WHERE instead",
                verify("SELECT int FROM test GROUP BY int HAVING int > 2"));
    }

    public void testHavingOnScalar() {
        assertEquals("1:42: Cannot filter HAVING on non-aggregate [int]; consider using WHERE instead",
                verify("SELECT int FROM test GROUP BY int HAVING 2 < ABS(int)"));
    }

    public void testInWithDifferentDataTypes_SelectClause() {
        assertEquals("1:17: expected data type [INTEGER], value provided is of type [KEYWORD]",
            verify("SELECT 1 IN (2, '3', 4)"));
    }

    public void testInNestedWithDifferentDataTypes_SelectClause() {
        assertEquals("1:27: expected data type [INTEGER], value provided is of type [KEYWORD]",
            verify("SELECT 1 = 1  OR 1 IN (2, '3', 4)"));
    }

    public void testInWithDifferentDataTypesFromLeftValue_SelectClause() {
        assertEquals("1:14: expected data type [INTEGER], value provided is of type [KEYWORD]",
            verify("SELECT 1 IN ('foo', 'bar')"));
    }

    public void testInNestedWithDifferentDataTypesFromLeftValue_SelectClause() {
        assertEquals("1:29: expected data type [KEYWORD], value provided is of type [INTEGER]",
            verify("SELECT 1 = 1  OR  'foo' IN (2, 3)"));
    }

    public void testInWithDifferentDataTypes_WhereClause() {
        assertEquals("1:49: expected data type [TEXT], value provided is of type [INTEGER]",
            verify("SELECT * FROM test WHERE text IN ('foo', 'bar', 4)"));
    }

    public void testInNestedWithDifferentDataTypes_WhereClause() {
        assertEquals("1:60: expected data type [TEXT], value provided is of type [INTEGER]",
            verify("SELECT * FROM test WHERE int = 1 OR text IN ('foo', 'bar', 2)"));
    }

    public void testInWithDifferentDataTypesFromLeftValue_WhereClause() {
        assertEquals("1:35: expected data type [TEXT], value provided is of type [INTEGER]",
            verify("SELECT * FROM test WHERE text IN (1, 2)"));
    }

    public void testInNestedWithDifferentDataTypesFromLeftValue_WhereClause() {
        assertEquals("1:46: expected data type [TEXT], value provided is of type [INTEGER]",
            verify("SELECT * FROM test WHERE int = 1 OR text IN (1, 2)"));
    }

    public void testNotSupportedAggregateOnDate() {
        assertEquals("1:8: [AVG] argument must be [numeric], found value [date] type [date]",
            verify("SELECT AVG(date) FROM test"));
    }

    public void testNotSupportedAggregateOnString() {
        assertEquals("1:8: [MAX] argument must be [numeric or date], found value [keyword] type [keyword]",
            verify("SELECT MAX(keyword) FROM test"));
    }

    public void testInvalidTypeForStringFunction_WithOneArg() {
        assertEquals("1:8: [LENGTH] argument must be [string], found value [1] type [integer]",
            verify("SELECT LENGTH(1)"));
    }

    public void testInvalidTypeForNumericFunction_WithOneArg() {
        assertEquals("1:8: [COS] argument must be [numeric], found value [foo] type [keyword]",
            verify("SELECT COS('foo')"));
    }

    public void testInvalidTypeForBooleanFunction_WithOneArg() {
        assertEquals("1:8: [NOT] argument must be [boolean], found value [foo] type [keyword]",
            verify("SELECT NOT 'foo'"));
    }

    public void testInvalidTypeForStringFunction_WithTwoArgs() {
        assertEquals("1:8: [CONCAT] first argument must be [string], found value [1] type [integer]",
            verify("SELECT CONCAT(1, 'bar')"));
        assertEquals("1:8: [CONCAT] second argument must be [string], found value [2] type [integer]",
            verify("SELECT CONCAT('foo', 2)"));
    }

    public void testInvalidTypeForNumericFunction_WithTwoArgs() {
        assertEquals("1:8: [TRUNCATE] first argument must be [numeric], found value [foo] type [keyword]",
            verify("SELECT TRUNCATE('foo', 2)"));
        assertEquals("1:8: [TRUNCATE] second argument must be [numeric], found value [bar] type [keyword]",
            verify("SELECT TRUNCATE(1.2, 'bar')"));
    }

    public void testInvalidTypeForBooleanFuntion_WithTwoArgs() {
        assertEquals("1:8: [OR] first argument must be [boolean], found value [1] type [integer]",
            verify("SELECT 1 OR true"));
        assertEquals("1:8: [OR] second argument must be [boolean], found value [2] type [integer]",
            verify("SELECT true OR 2"));
    }

    public void testInvalidTypeForFunction_WithThreeArgs() {
        assertEquals("1:8: [REPLACE] first argument must be [string], found value [1] type [integer]",
            verify("SELECT REPLACE(1, 'foo', 'bar')"));
        assertEquals("1:8: [REPLACE] second argument must be [string], found value [2] type [integer]",
            verify("SELECT REPLACE('text', 2, 'bar')"));
        assertEquals("1:8: [REPLACE] third argument must be [string], found value [3] type [integer]",
            verify("SELECT REPLACE('text', 'foo', 3)"));
    }

    public void testInvalidTypeForFunction_WithFourArgs() {
        assertEquals("1:8: [INSERT] first argument must be [string], found value [1] type [integer]",
            verify("SELECT INSERT(1, 1, 2, 'new')"));
        assertEquals("1:8: [INSERT] second argument must be [numeric], found value [foo] type [keyword]",
            verify("SELECT INSERT('text', 'foo', 2, 'new')"));
        assertEquals("1:8: [INSERT] third argument must be [numeric], found value [bar] type [keyword]",
            verify("SELECT INSERT('text', 1, 'bar', 'new')"));
        assertEquals("1:8: [INSERT] fourth argument must be [string], found value [3] type [integer]",
            verify("SELECT INSERT('text', 1, 2, 3)"));
    }
}
