// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.starrocks.sql.plan;

import com.google.common.collect.Sets;
import com.starrocks.analysis.AnalyticExpr;
import com.starrocks.sql.analyzer.SemanticException;
import com.starrocks.utframe.StarRocksAssert;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ArrayTypeTest extends PlanTestBase {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @BeforeClass
    public static void beforeClass() throws Exception {
        PlanTestBase.beforeClass();
        StarRocksAssert starRocksAssert = new StarRocksAssert(connectContext);
        starRocksAssert.withTable("create table test_array(c0 INT, c1 array<varchar(65533)>, c2 array<int>) " +
                " duplicate key(c0) distributed by hash(c0) buckets 1 " +
                "properties('replication_num'='1');");

        starRocksAssert.withTable("CREATE TABLE adec ( \n" +
                "v1 bigint not null ,\n" +
                "i_1 Array<INT> NOT NULL ,\n" +
                "s_1 Array<String> NULL ,\n" +
                "d_1 Array<DECIMAL(26, 2)> NOT NULL ,\n" +
                "d_2 Array<DECIMAL64(4, 3)> NULL ,\n" +
                "d_3 Array<DECIMAL128(25, 19)> NOT NULL ,\n" +
                "d_4 Array<DECIMAL32(8, 5)> NULL ,\n" +
                "d_5 Array<DECIMAL(16, 3)> NULL ,\n" +
                "d_6 Array<DECIMAL128(18, 6)> NOT NULL \n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`v1`)\n" +
                "DISTRIBUTED BY HASH(`v1`) BUCKETS 3\n" +
                "PROPERTIES (\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"DEFAULT\"\n" +
                ");");
    }

    @Test
    public void testSelectArrayElementFromArrayColumn() throws Exception {
        String sql = "select v3[1] from tarray";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("1:Project\n" +
                "  |  <slot 4> : 3: v3[1]"));
    }

    @Test
    public void testArrayElementWithFunction() throws Exception {
        String sql = "select v1, sum(v3[1]) from tarray group by v1";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("1:Project\n" +
                "  |  <slot 1> : 1: v1\n" +
                "  |  <slot 4> : 3: v3[1]"));
    }

    @Test
    public void testArrayCountDistinctWithOrderBy() throws Exception {
        String sql = "select distinct v3 from tarray order by v3[1];";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("2:Project\n" +
                "  |  <slot 3> : 3: v3\n" +
                "  |  <slot 4> : 3: v3[1]"));
    }

    @Test
    public void testArrayElementExpr() throws Exception {
        String sql = "select [][1] + 1, [1,2,3][1] + [[1,2,3],[1,1,1]][2][2]";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("  |  <slot 2> : NULL\n" +
                "  |  <slot 3> : CAST(ARRAY<tinyint(4)>[1,2,3][1] AS SMALLINT) + " +
                "CAST(ARRAY<ARRAY<tinyint(4)>>[[1,2,3],[1,1,1]][2][2] AS SMALLINT)"));

        sql = "select v1, v3[1] + [1,2,3][1] as v, sum(v3[1]) from tarray group by v1, v order by v";
        plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("2:AGGREGATE (update finalize)\n" +
                "  |  output: sum(5: expr)\n" +
                "  |  group by: 1: v1, 4: expr\n" +
                "  |  \n" +
                "  1:Project\n" +
                "  |  <slot 1> : 1: v1\n" +
                "  |  <slot 4> : 3: v3[1] + CAST(ARRAY<tinyint(4)>[1,2,3][1] AS BIGINT)\n" +
                "  |  <slot 5> : 3: v3[1]\n"));
    }

    @Test
    public void testSelectDistinctArrayWithOrderBy() throws Exception {
        String sql = "select distinct v1 from tarray order by v1+1";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("2:Project\n" +
                "  |  <slot 1> : 1: v1\n" +
                "  |  <slot 4> : 1: v1 + 1"));
    }

    @Test
    public void testSelectDistinctArrayWithOrderBy2() throws Exception {
        String sql = "select distinct v1+1 as v from tarray order by v+1";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("3:Project\n" +
                "  |  <slot 4> : 4: expr\n" +
                "  |  <slot 5> : 4: expr + 1\n"));
        Assert.assertTrue(plan.contains("1:Project\n" +
                "  |  <slot 4> : 1: v1 + 1"));
    }

    @Test
    public void testSelectMultidimensionalArray() throws Exception {
        String sql = "select [[1,2],[3,4]][1][2]";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("ARRAY<ARRAY<tinyint(4)>>[[1,2],[3,4]][1][2]"));
    }

    @Test
    public void testSelectArrayElement() throws Exception {
        String sql = "select [1,2][1]";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("ARRAY<tinyint(4)>[1,2][1]"));

        sql = "select [][1]";
        plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("ARRAY<boolean>[][1]"));

        sql = "select [][1] from t0";
        plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("ARRAY<boolean>[][1]"));

        sql = "select [][1] from (values(1,2,3), (4,5,6)) t";
        plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("ARRAY<boolean>[][1]"));

        sql = "select [v1,v2] from t0";
        plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("1:Project\n" +
                "  |  <slot 4> : ARRAY<bigint(20)>[1: v1,2: v2]"));

        sql = "select [v1 = 1, v2 = 2, true] from t0";
        plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("<slot 4> : ARRAY<boolean>[1: v1 = 1,2: v2 = 2,TRUE]"));
    }

    @Test
    public void testCountDistinctArray() throws Exception {
        String sql = "select count(*), count(c1), count(distinct c1) from test_array";
        String planFragment = getFragmentPlan(sql);
        Assert.assertTrue(planFragment.contains("AGGREGATE (merge serialize)"));
    }

    @Test
    public void testArrayFunctionFilter() throws Exception {
        String sql = "select * from test_array where array_length(c1) between 2 and 3;";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("PREDICATES: array_length(2: c1) >= 2, array_length(2: c1) <= 3"));
    }

    @Test
    public void testArrayDifferenceArgs1() throws Exception {
        String sql = "select array_difference(c2) from test_array";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("array_difference(3: c2)"));

        sql = "select array_difference(c1) from test_array";
        expectedEx.expect(SemanticException.class);
        expectedEx.expectMessage("array_difference function only support numeric array types");
        getFragmentPlan(sql);
    }

    @Test
    public void testArrayDifferenceArgs2() throws Exception {
        String sql = "select array_difference(c0) from test_array";
        expectedEx.expect(SemanticException.class);
        expectedEx.expectMessage("No matching function with signature: array_difference(int(11)).");
        getFragmentPlan(sql);
    }

    @Test
    public void testArrayDifferenceArgs3() throws Exception {
        String sql = "select array_difference(c1, 3) from test_array";
        expectedEx.expect(SemanticException.class);
        expectedEx.expectMessage(
                "No matching function with signature: array_difference(ARRAY<varchar(65533)>, tinyint(4)).");
        getFragmentPlan(sql);
    }

    @Test
    public void testArrayDifferenceNullAndEmpty() throws Exception {
        String sql = "select array_difference(null)";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("NULL"));

        sql = "select array_difference([])";
        plan = getFragmentPlan(sql);
        Assert.assertTrue(plan.contains("array_difference"));
    }

    @Test
    public void testArrayClone() throws Exception {
        String sql =
                "select array_contains([v],1), array_contains([v],2) " +
                        "from (select v1+1 as v from t0,t1 group by v) t group by 1,2";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan, plan.contains("9:Project\n" +
                "  |  <slot 8> : array_contains(ARRAY<bigint(20)>[7: expr], 1)\n" +
                "  |  <slot 9> : array_contains(ARRAY<bigint(20)>[7: expr], 2)"));
    }

    @Test
    public void testArrayWindowFunction() throws Exception {
        for (String fnName : Sets.newHashSet(AnalyticExpr.LASTVALUE, AnalyticExpr.FIRSTVALUE)) {
            String sql = String.format("select %s(v3) over() from tarray", fnName.toLowerCase());
            expectedEx.expect(SemanticException.class);
            expectedEx.expectMessage(
                    String.format("No matching function with signature: %s(ARRAY<bigint(20)>)", fnName.toLowerCase()));
            getFragmentPlan(sql);
        }
    }

    @Test
    public void testEmptyArray() throws Exception {
        {
            String sql = "select cast([] as array<varchar(200)>)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  1:Project\n" +
                    "  |  <slot 2> : CAST(ARRAY<boolean>[] AS ARRAY<VARCHAR(200)>)\n" +
                    "  |  \n" +
                    "  0:UNION\n" +
                    "     constant exprs: \n" +
                    "         NULL");

            String thriftPlan = getThriftPlan(sql);
            assertNotContains(thriftPlan, "NULL_TYPE");
        }
        {
            String sql = "select cast(null as array<varchar(200)>)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  1:Project\n" +
                    "  |  <slot 2> : NULL\n" +
                    "  |  \n" +
                    "  0:UNION\n" +
                    "     constant exprs: \n" +
                    "         NULL");
        }
        {
            String sql = "select array_append([[1,2,3]], [])";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "<slot 2> : array_append(ARRAY<ARRAY<tinyint(4)>>[[1,2,3]], ARRAY<tinyint(4)>[])");
        }
        {
            String sql = "select array_append([[1,2,3]], [null])";
            String plan = getFragmentPlan(sql);
            assertContains(plan,
                    "<slot 2> : array_append(ARRAY<ARRAY<tinyint(4)>>[[1,2,3]], ARRAY<tinyint(4)>[NULL])");
        }
        {
            starRocksAssert.withTable("create table test_literal_array_insert_t0(" +
                    "c0 bigint, " +
                    "c1 ARRAY<ARRAY<bigint>> not null, " +
                    "c2 array<Array<bigint>> not null" +
                    ")" +
                    "duplicate key(c0) " +
                    "distributed by hash(c0) " +
                    "buckets 1 properties('replication_num'='1');\n");

            String sql = "insert into test_literal_array_insert_t0 values " +
                    "(4,[],[])";
            getFragmentPlan(sql);

            sql = "insert into test_literal_array_insert_t0 values " +
                    "(4,[],[]), " +
                    "(9223372036854775807,[[9223372036854775807]],[[9223372036854775807]]);\n";
            getFragmentPlan(sql);
        }
        {
            String sql = "select array_append([],null)";
            getThriftPlan(sql);
        }
        {
            String sql = "select [][1]";
            getThriftPlan(sql);
        }
        {
            String sql = "select array_append([], [])";
            getThriftPlan(sql);
        }
        {
            String sql = "select array_append([[]], [])";
            getThriftPlan(sql);
        }
    }

    @Test
    public void testNestedArrayLambdaFunctions() throws Exception {
        String sql = "WITH `CASE_006` AS\n" +
                "  (SELECT array_map((arg_001) -> (arg_001), `c1`) AS `argument_003`,\n" +
                "          array_map((arg_002) -> (CAST(1 AS BIGINT)), `c1`) AS `argument_004`\n" +
                "   FROM test_array)\n" +
                "\n" +
                "select argument_004, ARRAY_FILTER((x, y) -> y IS NOT NULL, " +
                "`argument_003`, `argument_004`) AS `source_target_005` from CASE_006;";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "  |  common expressions:\n" +
                "  |  <slot 14> : array_map(<slot 5> -> 1, 8: c1)");

        sql = "WITH `CASE_006` AS\n" +
                "  (SELECT array_map((arg_001) -> (arg_001), `c1`) AS `argument_003`,\n" +
                "          array_map((arg_002) -> (arg_002 + 1), `c1`) AS `argument_004`\n" +
                "   FROM test_array)\n" +
                "\n" +
                "select argument_004, ARRAY_FILTER((x, y) -> y IS NOT NULL, " +
                "`argument_003`, `argument_004`) AS `source_target_005` from CASE_006;";
        plan = getFragmentPlan(sql);
        assertContains(plan, "  |  common expressions:\n" +
                "  |  <slot 14> : array_map(<slot 5> -> CAST(<slot 5> AS DOUBLE) + 1.0, 8: c1)");
    }

    @Test
    public void testArraySortDecimalType() throws Exception {
        String sql = "select array_sort(d_1) from adec;";
        String plan = getVerboseExplain(sql);
        assertContains(plan, "array_sort[([4: d_1, ARRAY<DECIMAL128(26,2)>, false]); " +
                "args: INVALID_TYPE; result: ARRAY<DECIMAL128(26,2)>;");

        sql = "select array_sort(d_2) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, "array_sort[([5: d_2, ARRAY<DECIMAL64(4,3)>, true]); " +
                "args: INVALID_TYPE; result: ARRAY<DECIMAL64(4,3)>;");

        sql = "select array_sort(d_4) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, "array_sort[([7: d_4, ARRAY<DECIMAL32(8,5)>, true]);" +
                " args: INVALID_TYPE; result: ARRAY<DECIMAL32(8,5)>;");

        sql = "select array_sort(d_5) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, "array_sort[([8: d_5, ARRAY<DECIMAL64(16,3)>, true]); " +
                "args: INVALID_TYPE; result: ARRAY<DECIMAL64(16,3)>;");
    }

    @Test
    public void testArrayIntersectDecimalType() throws Exception {
        String sql = "select array_intersect(d_1, d_2) from adec;";
        String plan = getVerboseExplain(sql);
        assertContains(plan,
                "array_intersect[(cast([4: d_1, ARRAY<DECIMAL128(26,2)>, false] as ARRAY<DECIMAL128(27,3)>), " +
                        "cast([5: d_2, ARRAY<DECIMAL64(4,3)>, true] as ARRAY<DECIMAL128(27,3)>)); " +
                        "args: INVALID_TYPE,INVALID_TYPE; result: ARRAY<DECIMAL128(27,3)>;");

        sql = "select array_intersect(d_1, d_3) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, " array_intersect[(cast([4: d_1, ARRAY<DECIMAL128(26,2)>, false] as ARRAY<DOUBLE>), " +
                "cast([6: d_3, ARRAY<DECIMAL128(25,19)>, false] as ARRAY<DOUBLE>)); args: INVALID_TYPE,INVALID_TYPE; " +
                "result: ARRAY<DOUBLE>;");

        sql = "select array_intersect(d_3, d_4) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, "array_intersect[([6: d_3, ARRAY<DECIMAL128(25,19)>, false], " +
                "cast([7: d_4, ARRAY<DECIMAL32(8,5)>, true] as ARRAY<DECIMAL128(25,19)>)); args: INVALID_TYPE,INVALID_TYPE; " +
                "result: ARRAY<DECIMAL128(25,19)>;");

        sql = "select array_intersect(d_3, d_6) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, "array_intersect[([6: d_3, ARRAY<DECIMAL128(25,19)>, false], " +
                "cast([9: d_6, ARRAY<DECIMAL128(18,6)>, false] as ARRAY<DECIMAL128(31,19)>)); args: INVALID_TYPE,INVALID_TYPE; " +
                "result: ARRAY<DECIMAL128(31,19)>;");

        sql = "select array_intersect(d_1, i_1) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, "10 <-> array_intersect[([4: d_1, ARRAY<DECIMAL128(26,2)>, false], " +
                "cast([2: i_1, ARRAY<INT>, false] as ARRAY<DECIMAL128(26,2)>)); args: INVALID_TYPE,INVALID_TYPE; " +
                "result: ARRAY<DECIMAL128(26,2)>;");

        sql = "select array_intersect(d_3, s_1) from adec;";
        plan = getVerboseExplain(sql);
        assertContains(plan, "array_intersect[(cast([6: d_3, ARRAY<DECIMAL128(25,19)>, false] " +
                "as ARRAY<VARCHAR(65533)>), [3: s_1, ARRAY<VARCHAR(65533)>, true]); args: INVALID_TYPE,INVALID_TYPE; " +
                "result: ARRAY<VARCHAR(65533)>;");
    }
}
