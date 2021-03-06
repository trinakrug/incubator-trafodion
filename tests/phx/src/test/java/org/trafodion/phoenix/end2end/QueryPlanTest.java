// @@@ START COPYRIGHT @@@
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// @@@ END COPYRIGHT @@@

/*******************************************************************************
 * Copyright (c) 2013, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *     Neither the name of Salesforce.com nor the names of its contributors may
 *     be used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package test.java.org.trafodion.phoenix.end2end;

import static org.junit.Assert.*;
import org.junit.*;
import java.sql.*;
import java.util.*;

public class QueryPlanTest extends BaseTest {
   
    @BeforeClass
    public static void doTestSuiteSetup() throws Exception {
        /* List all of the object names being used in this entire class.
         * The objects are dropped with errors ignored, so it is OK if the
         * object does not exist for a particular test.
         */
        objDropList = new ArrayList<String>(
            Arrays.asList("table " + ATABLE_NAME, "table " + PTSDB_NAME, "table " + PTSDB3_NAME));
        doBaseTestSuiteSetup();
    }
    /* @AfterClass, @Before, @After are defined in BaseTest */

    @Test
    public void testExplainPlan() throws Exception {
        printTestDescription();

        createTestTable(ATABLE_NAME);
        createTestTable(PTSDB_NAME);
        createTestTable(PTSDB3_NAME);
        String[] queryPlans = null;
        if (tgtPH()) queryPlans = new String[] {
                "SELECT * FROM atable",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE",

                "SELECT inst,host FROM PTSDB WHERE REGEXP_SUBSTR(INST, '[^-]+', 1) IN ('na1', 'na2','na3')", // REVIEW: should this use skip scan given the regexpr_substr
                "CLIENT PARALLEL 1-WAY SKIP SCAN ON 3 RANGES OVER PTSDB ['na1'-'na2')...['na3'-'na4')\n" + 
                "    SERVER FILTER BY FIRST KEY ONLY AND REGEXP_SUBSTR(INST, '[^-]+', 1) IN ('na1','na2','na3')",

                "SELECT inst,host FROM PTSDB WHERE inst IN ('na1', 'na2','na3') AND host IN ('a','b') AND date >= to_date('2013-01-01') AND date < to_date('2013-01-02')",
                "CLIENT PARALLEL 1-WAY SKIP SCAN ON 6 RANGES OVER PTSDB 'na1'...'na3','a'...'b',['2013-01-01'-'2013-01-02')\n" + 
                "    SERVER FILTER BY FIRST KEY ONLY",

                "SELECT inst,host FROM PTSDB WHERE inst LIKE 'na%' AND host IN ('a','b') AND date >= to_date('2013-01-01') AND date < to_date('2013-01-02')",
                "CLIENT PARALLEL 1-WAY SKIP SCAN ON 2 RANGES OVER PTSDB ['na'-'nb'),'a'...'b',['2013-01-01'-'2013-01-02')\n" + 
                "    SERVER FILTER BY FIRST KEY ONLY",

                "SELECT host FROM PTSDB3 WHERE host IN ('na1', 'na2','na3')",
                "CLIENT PARALLEL 1-WAY SKIP SCAN ON 3 KEYS OVER PTSDB3 'na3'...'na1'\n" + 
                "    SERVER FILTER BY FIRST KEY ONLY",

                "SELECT count(*) FROM atable",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" + 
                "    SERVER FILTER BY FIRST KEY ONLY\n" + 
                "    SERVER AGGREGATE INTO SINGLE ROW",

                // TODO: review: why does this change with parallelized non aggregate queries?
                "SELECT count(*) FROM atable WHERE organization_id='000000000000001' AND SUBSTR(entity_id,1,3) > '002' AND SUBSTR(entity_id,1,3) <= '003'",
                "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001',['003'-'004')\n" + 
                "    SERVER FILTER BY FIRST KEY ONLY\n" + 
                "    SERVER AGGREGATE INTO SINGLE ROW",

                "SELECT a_string FROM atable WHERE organization_id='000000000000001' AND SUBSTR(entity_id,1,3) > '002' AND SUBSTR(entity_id,1,3) <= '003'",
                "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001',['003'-'004')",

                "SELECT count(1) FROM atable GROUP BY a_string",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
                "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING]\n" +
                "CLIENT MERGE SORT",

                "SELECT count(1) FROM atable GROUP BY a_string LIMIT 5",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" + 
                "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING]\n" + 
                "CLIENT MERGE SORT\n" + 
                "CLIENT 5 ROW LIMIT",

                "SELECT a_string FROM atable ORDER BY a_string DESC LIMIT 3",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" + 
                "    SERVER TOP 3 ROWS SORTED BY [A_STRING DESC]\n" + 
                "CLIENT MERGE SORT",

                "SELECT count(1) FROM atable GROUP BY a_string,b_string HAVING max(a_string) = 'a'",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
                "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING, B_STRING]\n" +
                "CLIENT MERGE SORT\n" +
                "CLIENT FILTER BY MAX(A_STRING) = 'a'",

                "SELECT count(1) FROM atable WHERE a_integer = 1 GROUP BY ROUND(a_time,'HOUR',2),entity_id HAVING max(a_string) = 'a'",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
                "    SERVER FILTER BY A_INTEGER = 1\n" +
                "    SERVER AGGREGATE INTO DISTINCT ROWS BY [ENTITY_ID, ROUND(A_TIME)]\n" +
                "CLIENT MERGE SORT\n" +
                "CLIENT FILTER BY MAX(A_STRING) = 'a'",

                "SELECT count(1) FROM atable WHERE a_integer = 1 GROUP BY a_string,b_string HAVING max(a_string) = 'a' ORDER BY b_string",
                "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
                "    SERVER FILTER BY A_INTEGER = 1\n" +
                "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING, B_STRING]\n" +
                "CLIENT MERGE SORT\n" +
                "CLIENT FILTER BY MAX(A_STRING) = 'a'\n" +
                "CLIENT SORTED BY [B_STRING]",

                "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' AND entity_id = '000000000000002' AND x_integer = 2 AND a_integer < 5 ",
                "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001','000000000000002'\n" + 
                "    SERVER FILTER BY (X_INTEGER = 2 AND A_INTEGER < 5)",

                "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' AND entity_id != '000000000000002' AND x_integer = 2 AND a_integer < 5 LIMIT 10",
                "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" + 
                "    SERVER FILTER BY (ENTITY_ID != '000000000000002' AND X_INTEGER = 2 AND A_INTEGER < 5)\n" + 
                "    SERVER 10 ROW LIMIT\n" + 
                "CLIENT 10 ROW LIMIT",

                "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string ASC NULLS FIRST LIMIT 10",
                "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" + 
                "    SERVER TOP 10 ROWS SORTED BY [A_STRING]\n" + 
                "CLIENT MERGE SORT",

                "SELECT max(a_integer) FROM atable WHERE organization_id = '000000000000001' GROUP BY organization_id,entity_id,ROUND(a_date,'HOUR') ORDER BY entity_id NULLS LAST LIMIT 10",
                "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" + 
                "    SERVER AGGREGATE INTO DISTINCT ROWS BY [ORGANIZATION_ID, ENTITY_ID, ROUND(A_DATE)]\n" + 
                "CLIENT MERGE SORT\n" + 
                "CLIENT TOP 10 ROWS SORTED BY [ENTITY_ID NULLS LAST]",

                "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string DESC NULLS LAST LIMIT 10",
                "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" + 
                "    SERVER TOP 10 ROWS SORTED BY [A_STRING DESC NULLS LAST]\n" + 
                "CLIENT MERGE SORT",

                "SELECT a_string,b_string FROM atable WHERE organization_id IN ('000000000000001', '000000000000005')",
                "CLIENT PARALLEL 1-WAY SKIP SCAN ON 2 KEYS OVER ATABLE '000000000000001'...'000000000000005'",

                "SELECT a_string,b_string FROM atable WHERE organization_id IN ('00D000000000001', '00D000000000005') AND entity_id IN('00E00000000000X','00E00000000000Z')",
                "CLIENT PARALLEL 1-WAY SKIP SCAN ON 4 KEYS OVER ATABLE '00D000000000001'...'00D000000000005','00E00000000000X'...'00E00000000000Z'",
            };
        // TRAF TODO: The 2nd string in each pair will need to be adjusted
        // once we know what we are looking for in each plan.
        else if (tgtTR()) queryPlans = new String[] {
                "SELECT * FROM atable",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE",
/* TRAF */      "seabase_scan",
// TRAF                 "SELECT inst,host FROM PTSDB WHERE REGEXP_SUBSTR(INST, '[^-]+', 1) IN ('na1', 'na2','na3')", // REVIEW: should this use skip scan given the regexpr_substr
/* TRAF */      "SELECT inst,host1 FROM PTSDB WHERE INST IN ('na1', 'na2','na3')", // REVIEW: should this use skip scan given the regexpr_substr
// TRAF         "CLIENT PARALLEL 1-WAY SKIP SCAN ON 3 RANGES OVER PTSDB ['na1'-'na2')...['na3'-'na4')\n" +
// TRAF         "    SERVER FILTER BY FIRST KEY ONLY AND REGEXP_SUBSTR(INST, '[^-]+', 1) IN ('na1','na2','na3')",
/* TRAF */      "seabase_scan",
// TRAF         "SELECT inst,host FROM PTSDB WHERE inst IN ('na1', 'na2','na3') AND host IN ('a','b') AND date >= to_date('2013-01-01') AND date < to_date('2013-01-02')",
/* TRAF */      "SELECT inst,host1 FROM PTSDB WHERE inst IN ('na1', 'na2','na3') AND host1 IN ('a','b') AND date1 >= TIMESTAMP '2013-01-01 00:00:00' AND date1 < TIMESTAMP '2013-01-02 00:00:00'",
// TRAF         "CLIENT PARALLEL 1-WAY SKIP SCAN ON 6 RANGES OVER PTSDB 'na1'...'na3','a'...'b',['2013-01-01'-'2013-01-02')\n" +
// TRAF         "    SERVER FILTER BY FIRST KEY ONLY",
/* TRAF */      "seabase_scan",


// TRAF         "SELECT inst,host FROM PTSDB WHERE inst LIKE 'na%' AND host IN ('a','b') AND date >= to_date('2013-01-01') AND date < to_date('2013-01-02')",
/* TRAF */      "SELECT inst,host1 FROM PTSDB WHERE inst LIKE 'na%' AND host1 IN ('a','b') AND date1 >= TIMESTAMP '2013-01-01 00:00:00' AND date1 < TIMESTAMP '2013-01-02 00:00:00'",
// TRAF         "CLIENT PARALLEL 1-WAY SKIP SCAN ON 2 RANGES OVER PTSDB ['na'-'nb'),'a'...'b',['2013-01-01'-'2013-01-02')\n" +
// TRAF         "    SERVER FILTER BY FIRST KEY ONLY",
/* TRAF */      "seabase_scan",
// TRAF         "SELECT host FROM PTSDB3 WHERE host IN ('na1', 'na2','na3')",
/* TRAF */      "SELECT host1 FROM PTSDB3 WHERE host1 IN ('na1', 'na2','na3')",
// TRAF         "CLIENT PARALLEL 1-WAY SKIP SCAN ON 3 KEYS OVER PTSDB3 'na3'...'na1'\n" +
// TRAF         "    SERVER FILTER BY FIRST KEY ONLY",
/* TRAF */      "seabase_scan",
                "SELECT count(*) FROM atable",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
// TRAF         "    SERVER FILTER BY FIRST KEY ONLY\n" +
// TRAF         "    SERVER AGGREGATE INTO SINGLE ROW",
/* TRAF */      "seabase_scan",
                // TODO: review: why does this change with parallelized non aggregate queries?
// TRAF         "SELECT count(*) FROM atable WHERE organization_id='000000000000001' AND SUBSTR(entity_id,1,3) > '002' AND SUBSTR(entity_id,1,3) <= '003'",
                "SELECT count(*) FROM atable WHERE organization_id='000000000000001' AND SUBSTR(entity_id,1,3) > '002' AND SUBSTR(entity_id,1,3) <= '003'",
// TRAF         "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001',['003'-'004')\n" +
// TRAF         "    SERVER FILTER BY FIRST KEY ONLY\n" +
// TRAF         "    SERVER AGGREGATE INTO SINGLE ROW",
/* TRAF */      "seabase_scan",
                "SELECT a_string FROM atable WHERE organization_id='000000000000001' AND SUBSTR(entity_id,1,3) > '002' AND SUBSTR(entity_id,1,3) <= '003'",
// TRAF         "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001',['003'-'004')",
/* TRAF */      "seabase_scan",
                "SELECT count(1) FROM atable GROUP BY a_string",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
// TRAF         "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING]\n" +
// TRAF         "CLIENT MERGE SORT",
/* TRAF */      "seabase_scan",
                "SELECT count(1) FROM atable GROUP BY a_string LIMIT 5",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
// TRAF         "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING]\n" +
// TRAF         "CLIENT MERGE SORT\n" +
// TRAF         "CLIENT 5 ROW LIMIT",
/* TRAF */      "seabase_scan",
                "SELECT a_string FROM atable ORDER BY a_string DESC LIMIT 3",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
// TRAF         "    SERVER TOP 3 ROWS SORTED BY [A_STRING DESC]\n" +
// TRAF         "CLIENT MERGE SORT",
/* TRAF */      "seabase_scan",
                "SELECT count(1) FROM atable GROUP BY a_string,b_string HAVING max(a_string) = 'a'",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
// TRAF         "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING, B_STRING]\n" +
// TRAF         "CLIENT MERGE SORT\n" +
// TRAF         "CLIENT FILTER BY MAX(A_STRING) = 'a'",
/* TRAF */      "seabase_scan",
// TRAF         "SELECT count(1) FROM atable WHERE a_integer = 1 GROUP BY ROUND(a_time,'HOUR',2),entity_id HAVING max(a_string) = 'a'",
/* TRAF */      "SELECT count(1) FROM atable WHERE a_integer = 1 GROUP BY a_time,entity_id HAVING max(a_string) = 'a'",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
// TRAF         "    SERVER FILTER BY A_INTEGER = 1\n" +
// TRAF         "    SERVER AGGREGATE INTO DISTINCT ROWS BY [ENTITY_ID, ROUND(A_TIME)]\n" +
// TRAF         "CLIENT MERGE SORT\n" +
// TRAF         "CLIENT FILTER BY MAX(A_STRING) = 'a'",
/* TRAF */      "seabase_scan",
// TRAF         "SELECT count(1) FROM atable WHERE a_integer = 1 GROUP BY a_string,b_string HAVING max(a_string) = 'a' ORDER BY b_string",
                "SELECT count(1), b_string FROM atable WHERE a_integer = 1 GROUP BY a_string,b_string HAVING max(a_string) = 'a' ORDER BY b_string",
// TRAF         "CLIENT PARALLEL 4-WAY FULL SCAN OVER ATABLE\n" +
// TRAF         "    SERVER FILTER BY A_INTEGER = 1\n" +
// TRAF         "    SERVER AGGREGATE INTO DISTINCT ROWS BY [A_STRING, B_STRING]\n" +
// TRAF         "CLIENT MERGE SORT\n" +
// TRAF         "CLIENT FILTER BY MAX(A_STRING) = 'a'\n" +
// TRAF         "CLIENT SORTED BY [B_STRING]",
/* TRAF */      "seabase_scan",
                "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' AND entity_id = '000000000000002' AND x_integer = 2 AND a_integer < 5 ",
// TRAF         "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001','000000000000002'\n" +
// TRAF         "    SERVER FILTER BY (X_INTEGER = 2 AND A_INTEGER < 5)",
/* TRAF */      "seabase_scan",
                "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' AND entity_id != '000000000000002' AND x_integer = 2 AND a_integer < 5 LIMIT 10",
// TRAF         "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" +
// TRAF         "    SERVER FILTER BY (ENTITY_ID != '000000000000002' AND X_INTEGER = 2 AND A_INTEGER < 5)\n" +
// TRAF         "    SERVER 10 ROW LIMIT\n" +
// TRAF         "CLIENT 10 ROW LIMIT",
/* TRAF */       "seabase_scan",
// TRAF         "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string ASC NULLS FIRST LIMIT 10",
/* TRAF */      "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string ASC LIMIT 10",
// TRAF         "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" +
// TRAF         "    SERVER TOP 10 ROWS SORTED BY [A_STRING]\n" +
// TRAF         "CLIENT MERGE SORT",
/* TRAF */      "seabase_scan",
// TRAF         "SELECT max(a_integer) FROM atable WHERE organization_id = '000000000000001' GROUP BY organization_id,entity_id,ROUND(a_date,'HOUR') ORDER BY entity_id NULLS LAST LIMIT 10",
/* TRAF */      "SELECT max(a_integer), entity_id FROM atable WHERE organization_id = '000000000000001' GROUP BY organization_id,entity_id,a_date ORDER BY entity_id LIMIT 10",
// TRAF         "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" +
// TRAF         "    SERVER AGGREGATE INTO DISTINCT ROWS BY [ORGANIZATION_ID, ENTITY_ID, ROUND(A_DATE)]\n" +
// TRAF         "CLIENT MERGE SORT\n" +
// TRAF         "CLIENT TOP 10 ROWS SORTED BY [ENTITY_ID NULLS LAST]",
/* TRAF */      "seabase_scan",
// TRAF         "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string DESC NULLS LAST LIMIT 10",
/* TRAF */      "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string DESC LIMIT 10",
// TRAF         "CLIENT PARALLEL 1-WAY RANGE SCAN OVER ATABLE '000000000000001'\n" +
// TRAF         "    SERVER TOP 10 ROWS SORTED BY [A_STRING DESC NULLS LAST]\n" +
// TRAF         "CLIENT MERGE SORT",
/* TRAF */      "seabase_scan",
                "SELECT a_string,b_string FROM atable WHERE organization_id IN ('000000000000001', '000000000000005')",
// TRAF         "CLIENT PARALLEL 1-WAY SKIP SCAN ON 2 KEYS OVER ATABLE '000000000000001'...'000000000000005'",
/* TRAF */      "seabase_scan",
                "SELECT a_string,b_string FROM atable WHERE organization_id IN ('00D000000000001', '00D000000000005') AND entity_id IN('00E00000000000X','00E00000000000Z')",
// TRAF         "CLIENT PARALLEL 1-WAY SKIP SCAN ON 4 KEYS OVER ATABLE '00D000000000001'...'00D000000000005','00E00000000000X'...'00E00000000000Z'",
/* TRAF */      "seabase_scan",
            };
        else if (tgtSQ()) queryPlans = new String[] {
                "SELECT * FROM atable",
                "file_scan",        
                "SELECT inst,host1 FROM PTSDB WHERE INST IN ('na1', 'na2','na3')", // REVIEW: should this use skip scan given the regexpr_substr
                "file_scan", 
                "SELECT inst,host1 FROM PTSDB WHERE inst IN ('na1', 'na2','na3') AND host1 IN ('a','b') AND date1 >= TIMESTAMP '2013-01-01 00:00:00' AND date1 < TIMESTAMP '2013-01-02 00:00:00'",
                "file_scan",
                "SELECT inst,host1 FROM PTSDB WHERE inst LIKE 'na%' AND host1 IN ('a','b') AND date1 >= TIMESTAMP '2013-01-01 00:00:00' AND date1 < TIMESTAMP '2013-01-02 00:00:00'",
                "file_scan",
                "SELECT host1 FROM PTSDB3 WHERE host1 IN ('na1', 'na2','na3')",
                "file_scan",
                "SELECT count(*) FROM atable",
                "file_scan",
                "SELECT count(*) FROM atable WHERE organization_id='000000000000001' AND SUBSTR(entity_id,1,3) > '002' AND SUBSTR(entity_id,1,3) <= '003'",
                "file_scan",
                "SELECT a_string FROM atable WHERE organization_id='000000000000001' AND SUBSTR(entity_id,1,3) > '002' AND SUBSTR(entity_id,1,3) <= '003'",
                "file_scan",
                "SELECT count(1) FROM atable GROUP BY a_string",
                "file_scan",
                "SELECT [first 5] count(1) FROM atable GROUP BY a_string",
                "file_scan",
                "SELECT [first 3] a_string FROM atable ORDER BY a_string DESC",
                "file_scan",
                "SELECT count(1) FROM atable GROUP BY a_string,b_string HAVING max(a_string) = 'a'",
                "file_scan",
                "SELECT count(1) FROM atable WHERE a_integer = 1 GROUP BY a_time,entity_id HAVING max(a_string) = 'a'",
                "file_scan",
                "SELECT count(1), b_string FROM atable WHERE a_integer = 1 GROUP BY a_string,b_string HAVING max(a_string) = 'a' ORDER BY b_string",
                "file_scan",  
                "SELECT a_string,b_string FROM atable WHERE organization_id = '000000000000001' AND entity_id = '000000000000002' AND x_integer = 2 AND a_integer < 5 ",
                "file_scan",
                "SELECT [first 10] a_string,b_string FROM atable WHERE organization_id = '000000000000001' AND entity_id != '000000000000002' AND x_integer = 2 AND a_integer < 5",
                "file_scan",
                "SELECT [first 10] a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string ASC",
                "file_scan",
                "SELECT [first 10] max(a_integer), entity_id FROM atable WHERE organization_id = '000000000000001' GROUP BY organization_id,entity_id,a_date ORDER BY entity_id",
                "file_scan",
                "SELECT [first 10] a_string,b_string FROM atable WHERE organization_id = '000000000000001' ORDER BY a_string DESC",
                "file_scan",
                "SELECT a_string,b_string FROM atable WHERE organization_id IN ('000000000000001', '000000000000005')",
                "file_scan",
                "SELECT a_string,b_string FROM atable WHERE organization_id IN ('00D000000000001', '00D000000000005') AND entity_id IN('00E00000000000X','00E00000000000Z')",
                "file_scan",
            };
        for (int i = 0; i < queryPlans.length; i+=2) {
            String query = queryPlans[i];
            String plan = queryPlans[i+1];
            try {
                Statement statement = conn.createStatement();
                ResultSet rs = null;
                // TODO: figure out a way of verifying that query isn't run during explain execution
                if (tgtPH()) {
                    rs = statement.executeQuery("EXPLAIN " + query);
                    assertEquals(query, plan, getExplainPlan(rs));
                } else if (tgtSQ()||tgtTR()) {
                     rs = conn.createStatement().executeQuery("EXPLAIN options 'f' " + query);
                     // TRAF query plan often changes.  Comment this out for now.
                     // assertTrue(query, getExplainPlan(rs).contains(plan));
               }
            } catch (Exception e) {
                throw new Exception(query + ": "+ e.getMessage(), e);
            } finally {
            }
        }
    }
}
