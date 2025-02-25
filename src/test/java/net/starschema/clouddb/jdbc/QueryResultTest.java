/**
 * Copyright (c) 2015, STARSCHEMA LTD. All rights reserved.
 *
 * <p>Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * <p>1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer. 2. Redistributions in binary form must reproduce the
 * above copyright notice, this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * <p>THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.starschema.clouddb.jdbc;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Junit tests if the query results return as expected
 *
 * @author Horváth Attila
 * @author Gunics Balazs
 */
public class QueryResultTest {

  private static java.sql.Connection con = null;
  // Logger logger = new Logger(QueryResultTest.class.getName());
  Logger logger = LoggerFactory.getLogger(QueryResultTest.class.getName());

  /**
   * Compares two String[][]
   *
   * @param expected
   * @param reality
   * @return true if they are equal false if not
   */
  private boolean comparer(String[][] expected, String[][] reality) {
    for (int i = 0; i < expected.length; i++) {
      for (int j = 0; j < expected[i].length; j++) {
        if (!(reality[i][j] == null && expected[i][j] == null)
            && !expected[i][j].equals(reality[i][j])) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Makes a new Bigquery Connection to Hardcoded URL and gives back the Connection to static con
   * member.
   */
  public void NewConnection(String extraUrl) {
    try {
      if (QueryResultTest.con == null || !QueryResultTest.con.isValid(0) || extraUrl != null) {

        this.logger.info("Testing the JDBC driver");
        try {
          Class.forName("net.starschema.clouddb.jdbc.BQDriver");
          String jdbcUrl =
              BQSupportFuncts.constructUrlFromPropertiesFile(
                  BQSupportFuncts.readFromPropFile(
                      getClass().getResource("/serviceaccount.properties").getFile()));
          if (extraUrl != null) {
            jdbcUrl += extraUrl;
          }
          QueryResultTest.con =
              DriverManager.getConnection(
                  jdbcUrl,
                  BQSupportFuncts.readFromPropFile(
                      getClass().getResource("/serviceaccount.properties").getFile()));
        } catch (Exception e) {
          this.logger.error("Error in connection" + e.toString());
          Assert.fail("General Exception:" + e.toString());
        }
        this.logger.info(((BQConnection) QueryResultTest.con).getURLPART());
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Before
  public void NewConnection() {
    NewConnection("&useLegacySql=true");
  }

  @After
  public void TeardownConnection() throws SQLException {
    QueryResultTest.con.close();
    QueryResultTest.con = null;
  }

  @Test
  public void QueryResultTestWithDataset() throws SQLException {
    NewConnection("&useLegacySql=false");

    QueryResultTest.con.setSchema("foobar");
    Assert.assertEquals("foobar", QueryResultTest.con.getSchema());

    QueryResultTest.con.setSchema("tokyo_star");
    Assert.assertEquals("tokyo_star", QueryResultTest.con.getSchema());

    final String sql = "SELECT meaning FROM meaning_of_life GROUP BY ROLLUP(meaning);";
    String[][] expectation = new String[][] {{null, "42"}};

    this.logger.info("Test Tokyo number: 1");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Statement s = QueryResultTest.con.createStatement();
      s.setMaxRows(1);
      Result = s.executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    HelperFunctions.printer(expectation);

    try {
      String[][] res = BQSupportMethods.GetQueryResult(Result);
      Assert.assertTrue(
          "Comparing failed in the String[][] array " + Arrays.deepToString(res),
          this.comparer(expectation, res));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest01() {
    final String sql = "SELECT TOP(word, 10), COUNT(*) FROM publicdata:samples.shakespeare";
    final String description = "The top 10 word from shakespeare #TOP #COUNT";
    String[][] expectation =
        new String[][] {
          {"you", "yet", "would", "world", "without", "with", "will", "why", "whose", "whom"},
          {"42", "42", "42", "42", "42", "42", "42", "42", "42", "42"}
        };

    this.logger.info("Test number: 01");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);
    HelperFunctions.printer(expectation);
    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }

    Assert.assertTrue(
        Result instanceof BQForwardOnlyResultSet || Result instanceof BQScrollableResultSet);

    long totalBytesProcessed;
    boolean cacheHit;
    if (Result instanceof BQForwardOnlyResultSet) {
      BQForwardOnlyResultSet bqForwardOnlyResultSet = (BQForwardOnlyResultSet) Result;
      totalBytesProcessed = bqForwardOnlyResultSet.getTotalBytesProcessed();
      cacheHit = bqForwardOnlyResultSet.getCacheHit();
    } else {
      BQScrollableResultSet bqScrollableResultSet = (BQScrollableResultSet) Result;
      totalBytesProcessed = bqScrollableResultSet.getTotalBytesProcessed();
      cacheHit = bqScrollableResultSet.getCacheHit();
    }

    Assert.assertEquals(cacheHit, totalBytesProcessed == 0);
  }

  @Test
  public void QueryResultTest02() {
    final String sql =
        "SELECT corpus FROM publicdata:samples.shakespeare GROUP BY corpus ORDER BY corpus LIMIT 5";
    final String description = "The book names of shakespeare #GROUP_BY #ORDER_BY";
    String[][] expectation =
        new String[][] {
          {"1kinghenryiv", "1kinghenryvi", "2kinghenryiv", "2kinghenryvi", "3kinghenryvi"}
        };
    this.logger.info("Test number: 02");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);
    HelperFunctions.printer(expectation);
    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest03() {
    final String sql =
        "SELECT COUNT(DISTINCT web100_log_entry.connection_spec.remote_ip) AS num_clients FROM"
            + " [guid754187384106:m_lab.2010_01] WHERE"
            + " IS_EXPLICITLY_DEFINED(web100_log_entry.connection_spec.remote_ip) AND"
            + " IS_EXPLICITLY_DEFINED(web100_log_entry.log_time) AND web100_log_entry.log_time >"
            + " 1262304000 AND web100_log_entry.log_time < 1262476800";
    final String description =
        "A sample query from google, but we don't have Access for the query table #ERROR"
            + " #accessDenied #403";

    this.logger.info("Test number: 03");
    this.logger.info("Running query:" + sql);
    this.logger.debug(description);
    java.sql.ResultSet result = null;
    try {
      Statement stmt = con.createStatement();
      // stmt.setQueryTimeout(60);
      result = stmt.executeQuery(sql);
    } catch (SQLException e) {
      this.logger.debug("SQLexception" + e.toString());
      Assert.assertTrue(
          e.toString().contains("Access Denied: Table guid754187384106:m_lab.2010_01"));
    }
  }

  @Test
  public void QueryResultTest04() {
    final String sql =
        "SELECT corpus FROM publicdata:samples.shakespeare WHERE LOWER(word)=\"lord\" GROUP BY"
            + " corpus ORDER BY corpus DESC LIMIT 5;";
    final String description = "A query which gets 5 of Shakespeare were the word lord is present";
    String[][] expectation =
        new String[][] {
          {"winterstale", "various", "twogentlemenofverona", "twelfthnight", "troilusandcressida"}
        };

    this.logger.info("Test number: 04");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);
    HelperFunctions.printer(expectation);
    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest05() {
    final String sql = "SELECT word FROM publicdata:samples.shakespeare WHERE word='huzzah' ;";
    final String description =
        "The word \"huzzah\" NOTE: It doesn't appear in any any book, so it returns with a null"
            + " #WHERE";

    this.logger.info("Test number: 05");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
      this.logger.debug("{}", Result.getMetaData().getColumnCount());
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);
    try {
      if (Result.getType() != ResultSet.TYPE_FORWARD_ONLY) Assert.assertFalse(Result.first());
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest06() {
    final String sql =
        "SELECT corpus_date,SUM(word_count) FROM publicdata:samples.shakespeare GROUP BY"
            + " corpus_date ORDER BY corpus_date DESC LIMIT 5;";
    final String description =
        "A query which gets how many words Shapespeare wrote in a year (5 years displayed"
            + " descending)";
    String[][] expectation =
        new String[][] {
          {"1612", "1611", "1610", "1609", "1608"},
          {"26265", "17593", "26181", "57073", "19846"}
        };

    this.logger.info("Test number: 06");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);
    HelperFunctions.printer(expectation);
    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest07() {
    final String sql =
        "SELECT corpus, SUM(word_count) as w_c FROM publicdata:samples.shakespeare GROUP BY corpus"
            + " HAVING w_c > 20000 ORDER BY w_c ASC LIMIT 5;";
    final String description =
        "A query which gets Shakespeare were there are more words then 20000 (only 5 is displayed"
            + " ascending)";
    String[][] expectation =
        new String[][] {
          {"juliuscaesar", "twelfthnight", "titusandronicus", "kingjohn", "tamingoftheshrew"},
          {"21052", "21633", "21911", "21983", "22358"}
        };

    this.logger.info("Test number: 07");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);

    HelperFunctions.printer(expectation);

    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest08() {
    final String sql =
        "SELECT corpus, MAX(word_count) as m, word FROM publicdata:samples.shakespeare GROUP BY"
            + " corpus,word ORDER BY m DESC LIMIT 5;";
    final String description =
        "A query which gets those Shakespeare with the most common word ordered by count descending"
            + " (only 5 is displayed)";
    String[][] expectation =
        new String[][] {
          {"hamlet", "coriolanus", "kinghenryv", "2kinghenryiv", "kingrichardiii"},
          {"995", "942", "937", "894", "848"},
          {"the", "the", "the", "the", "the"}
        };

    this.logger.info("Test number: 08");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);

    HelperFunctions.printer(expectation);

    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest09() {
    final String sql =
        "SELECT corpus, corpus_date FROM publicdata:samples.shakespeare GROUP BY corpus,"
            + " corpus_date ORDER BY corpus_date DESC LIMIT 3;";
    final String description = "Shakespeare's 3 latest";
    String[][] expectation =
        new String[][] {
          {"kinghenryviii", "tempest", "winterstale"},
          {"1612", "1611", "1610"}
        };

    this.logger.info("Test number: 09");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    this.logger.debug(description);

    HelperFunctions.printer(expectation);

    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTest10() {
    final String sql =
        "SELECT corpus_date,SUM(word_count) FROM publicdata:samples.shakespeare GROUP BY"
            + " corpus_date ORDER BY corpus_date DESC LIMIT 5;";
    // final String description =
    // "A query which gets how many words Shapespeare wrote in a year (5 years displayed
    // descending)";
    /*
     * String[][] expectation = new String[][] {
     * {"1612","1611","1610","1609","1608"},
     * {"26265","17593","26181","57073","19846"} };
     */

    this.logger.info("Test number: 10");
    this.logger.info("Running query:" + sql);

    try {
      Statement stmt = QueryResultTest.con.createStatement();
      stmt.setQueryTimeout(1);
      stmt.executeQuery(sql);
    } catch (SQLException e) {
      this.logger.info("SQLexception" + e.toString());
      Assert.assertTrue(true);
    }
  }

  @Test
  public void QueryResultTest11() {

    this.logger.info("Test number: 10");
    this.logger.info("Testing databesmetadata ... getSchemas() ");

    try {
      QueryResultTest.con.getMetaData().getSchemas();
    } catch (SQLException e) {
      this.logger.warn("SQLexception" + e.toString());
      Assert.fail("schema problem");
    }
  }

  @Test
  public void QueryResultTest12() {
    int limitNum = 40000;
    final String sql = "SELECT weight_pounds  FROM publicdata:samples.natality LIMIT " + limitNum;

    this.logger.info("Test number: 12");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Statement stm =
          con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
      stm.setFetchSize(1000);
      Result = stm.executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);
    try {
      /*
      int j = 0;
      for (int i = 0; i < limitNum-1; i++) {
          if(i%1000 == 0) {
              logger.debug("fetched 1k for the " + ++j + ". time");
          }
          Assert.assertTrue(Result.next());
      }*/
      Result.absolute(limitNum);
      Assert.assertTrue(true);
    } catch (SQLException e) {
      e.printStackTrace();
      Assert.fail();
    }
  }

  @Test
  public void QueryResultTestSyncQuery() {
    // sync is the default, but let's test it explicitly declared anyway
    NewConnection();
    final String sql =
        "SELECT STRING(ROUND(weight_pounds))  FROM publicdata:samples.natality GROUP BY 1 ORDER BY"
            + " 1 DESC LIMIT 10;";
    String[][] expectation =
        new String[][] {
          {
            "9.000000",
            "8.000000",
            "7.000000",
            "6.000000",
            "5.000000",
            "4.000000",
            "3.000000",
            "2.000000",
            "18.000000",
            "17.000000"
          }
        };

    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    HelperFunctions.printer(expectation);

    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTestTokyo01() {
    final String sql = "SELECT meaning FROM tokyo_star.meaning_of_life;";
    String[][] expectation = new String[][] {{"42"}};

    this.logger.info("Test Tokyo number: 1");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Result = QueryResultTest.con.createStatement().executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    HelperFunctions.printer(expectation);

    try {
      Assert.assertTrue(
          "Comparing failed in the String[][] array",
          this.comparer(expectation, BQSupportMethods.GetQueryResult(Result)));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }

  @Test
  public void QueryResultTestTokyoForceFetchMoreRowsPath() {
    NewConnection("&useLegacySql=false");
    final String sql = "SELECT meaning FROM tokyo_star.meaning_of_life GROUP BY ROLLUP(meaning);";
    String[][] expectation = new String[][] {{null, "42"}};

    this.logger.info("Test Tokyo number: 1");
    this.logger.info("Running query:" + sql);

    java.sql.ResultSet Result = null;
    try {
      Statement s = QueryResultTest.con.createStatement();
      s.setMaxRows(1);
      Result = s.executeQuery(sql);
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail("SQLException" + e.toString());
    }
    Assert.assertNotNull(Result);

    HelperFunctions.printer(expectation);

    try {
      String[][] res = BQSupportMethods.GetQueryResult(Result);
      Assert.assertTrue(
          "Comparing failed in the String[][] array " + Arrays.deepToString(res),
          this.comparer(expectation, res));
    } catch (SQLException e) {
      this.logger.error("SQLexception" + e.toString());
      Assert.fail(e.toString());
    }
  }
}
