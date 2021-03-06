/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.carbondata.integration.spark.testsuite.binary

import java.util.Arrays

import org.apache.carbondata.common.exceptions.sql.MalformedCarbonCommandException
import org.apache.carbondata.core.constants.CarbonCommonConstants
import org.apache.carbondata.core.metadata.CarbonMetadata
import org.apache.carbondata.core.metadata.schema.table.CarbonTable
import org.apache.carbondata.core.util.CarbonProperties

import org.apache.commons.codec.binary.Hex
import org.apache.spark.sql.Row
import org.apache.spark.sql.test.util.QueryTest
import org.apache.spark.util.SparkUtil
import org.scalatest.BeforeAndAfterAll

/**
  * Test cases for testing binary
  */
class TestBinaryDataType extends QueryTest with BeforeAndAfterAll {
    override def beforeAll {
    }

    test("Create table and load data with binary column") {
        sql("DROP TABLE IF EXISTS binaryTable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS binaryTable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
               | TBLPROPERTIES('SORT_COLUMNS'='')
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarydata.csv'
               | INTO TABLE binaryTable
               | OPTIONS('header'='false')
             """.stripMargin)

        val result = sql("desc formatted binaryTable").collect()
        var flag = false
        result.foreach { each =>
            if ("binary".equals(each.get(1))) {
                flag = true
            }
        }
        assert(flag)

        checkAnswer(sql("SELECT COUNT(*) FROM binaryTable"), Seq(Row(3)))
        try {
            val df = sql("SELECT * FROM binaryTable").collect()
            assert(3 == df.length)
            df.foreach { each =>
                assert(5 == each.length)

                assert(Integer.valueOf(each(0).toString) > 0)
                assert(each(1).toString.equalsIgnoreCase("false") || (each(1).toString.equalsIgnoreCase("true")))
                assert(each(2).toString.contains(".png"))

                val bytes40 = each.getAs[Array[Byte]](3).slice(0, 40)
                val binaryName = each(2).toString
                val expectedBytes = Hex.encodeHex(firstBytes20.get(binaryName).get)
                assert(Arrays.equals(String.valueOf(expectedBytes).getBytes(), bytes40), "incorrect numeric value for flattened binaryField")

                assert(each(4).toString.equalsIgnoreCase("false") || (each(4).toString.equalsIgnoreCase("true")))

                val df = sql("SELECT name,binaryField FROM binaryTable").collect()
                assert(3 == df.length)
                df.foreach { each =>
                    assert(2 == each.length)
                    val binaryName = each(0).toString
                    val bytes40 = each.getAs[Array[Byte]](1).slice(0, 40)
                    val expectedBytes = Hex.encodeHex(firstBytes20.get(binaryName).get)
                    assert(Arrays.equals(String.valueOf(expectedBytes).getBytes(), bytes40), "incorrect numeric value for flattened binaryField")
                }
            }
        } catch {
            case e: Exception =>
                e.printStackTrace()
                assert(false)
        }
    }

    private val firstBytes20 = Map("1.png" -> Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 1, 74),
        "2.png" -> Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 2, -11),
        "3.png" -> Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 1, 54)
    )

    test("Don't support sort_columns") {
        sql("DROP TABLE IF EXISTS binaryTable")
        val exception = intercept[Exception] {
            sql(
                s"""
                   | CREATE TABLE IF NOT EXISTS binaryTable (
                   |    id double,
                   |    label boolean,
                   |    name STRING,
                   |    binaryField BINARY,
                   |    autoLabel boolean)
                   | STORED BY 'carbondata'
                   | TBLPROPERTIES('SORT_COLUMNS'='binaryField')
             """.stripMargin)
        }
        assert(exception.getMessage.contains("sort_columns is unsupported for binary datatype column"))
    }

    test("Unsupport LOCAL_DICTIONARY_INCLUDE for binary") {

        sql("DROP TABLE IF EXISTS binaryTable")
        val exception = intercept[MalformedCarbonCommandException] {
            sql(
                """
                  | CREATE TABLE binaryTable(
                  |     id int,
                  |     name string,
                  |     city string,
                  |     age int,
                  |     binaryField binary)
                  | STORED BY 'org.apache.carbondata.format'
                  | tblproperties('local_dictionary_enable'='true','local_dictionary_include'='binaryField')
                """.stripMargin)
        }
        assert(exception.getMessage.contains(
            "LOCAL_DICTIONARY_INCLUDE/LOCAL_DICTIONARY_EXCLUDE column: binaryfield is not a string/complex/varchar datatype column. " +
                    "LOCAL_DICTIONARY_COLUMN should be no dictionary string/complex/varchar datatype column"))
    }

    test("Supports LOCAL_DICTIONARY_EXCLUDE for binary") {
        sql("DROP TABLE IF EXISTS binaryTable")
        sql(
            """
              | CREATE TABLE binaryTable(
              |     id int,
              |     name string,
              |     city string,
              |     age int,
              |     binaryField binary)
              | STORED BY 'org.apache.carbondata.format'
              | tblproperties('local_dictionary_enable'='true','LOCAL_DICTIONARY_EXCLUDE'='binaryField')
            """.stripMargin)
        assert(true)
    }

    test("Unsupport inverted_index for binary") {
        sql("DROP TABLE IF EXISTS binaryTable")
        val exception = intercept[MalformedCarbonCommandException] {
            sql(
                """
                  | CREATE TABLE binaryTable(
                  |     id int,
                  |     name string,
                  |     city string,
                  |     age int,
                  |     binaryField binary)
                  | STORED BY 'org.apache.carbondata.format'
                  | tblproperties('inverted_index'='binaryField')
                """.stripMargin)
        }
        assert(exception.getMessage.contains("INVERTED_INDEX column: binaryfield should be present in SORT_COLUMNS"))
    }

    test("Unsupport inverted_index and sort_columns for binary") {
        sql("DROP TABLE IF EXISTS binaryTable")
        val exception = intercept[MalformedCarbonCommandException] {
            sql(
                """
                  | CREATE TABLE binaryTable(
                  |     id int,
                  |     name string,
                  |     city string,
                  |     age int,
                  |     binaryField binary)
                  | STORED BY 'org.apache.carbondata.format'
                  | tblproperties('inverted_index'='binaryField','SORT_COLUMNS'='binaryField')
                """.stripMargin)
        }
        assert(exception.getMessage.contains("sort_columns is unsupported for binary datatype column: binaryfield"))
    }

    test("COLUMN_META_CACHE doesn't support binary") {
        sql("DROP TABLE IF EXISTS binaryTable")
        val exception = intercept[Exception] {
            sql(
                s"""
                   | CREATE TABLE IF NOT EXISTS binaryTable (
                   |    id INT,
                   |    label boolean,
                   |    name STRING,
                   |    binaryField BINARY,
                   |    autoLabel boolean)
                   | STORED BY 'carbondata'
                   | TBLPROPERTIES('COLUMN_META_CACHE'='binaryField')
             """.stripMargin)
        }
        assert(exception.getMessage.contains("binaryfield is a binary data type column and binary data type is not allowed for the option"))
    }

    test("RANGE_COLUMN doesn't support binary") {
        sql("DROP TABLE IF EXISTS binaryTable")
        val exception = intercept[Exception] {
            sql(
                s"""
                   | CREATE TABLE IF NOT EXISTS binaryTable (
                   |    id INT,
                   |    label boolean,
                   |    name STRING,
                   |    binaryField BINARY,
                   |    autoLabel boolean)
                   | STORED BY 'carbondata'
                   | TBLPROPERTIES('RANGE_COLUMN'='binaryField')
             """.stripMargin)
        }
        assert(exception.getMessage.contains("RANGE_COLUMN doesn't support binary data type"))
    }

    test("Test carbon.column.compressor=zstd") {
        sql("DROP TABLE IF EXISTS binaryTable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS binaryTable (
               |    id INT,
               |    label boolean,
               |    name STRING,
               |    binaryField BINARY,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
               | TBLPROPERTIES('carbon.column.compressor'='zstd')
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarydata.csv'
               | INTO TABLE binaryTable
               | OPTIONS('header'='false')
             """.stripMargin)
        checkAnswer(sql("SELECT COUNT(*) FROM binaryTable"), Seq(Row(3)))
        val value = sql("SELECT * FROM binaryTable").collect()
        value.foreach { each =>
            assert(5 == each.length)
            assert(1 == each.getAs[Int](0) || 2 == each.getAs[Int](0) || 3 == each.getAs[Int](0))
            assert(".png".equals(each.getAs(2).toString.substring(1, 5)))
            assert("89504e470d0a1a0a0000000d4948445200000".equals(new String(each.getAs[Array[Byte]](3).slice(0, 37))))
        }
    }

    test("Test carbon.column.compressor=gzip") {
        sql("DROP TABLE IF EXISTS binaryTable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS binaryTable (
               |    id INT,
               |    label boolean,
               |    name STRING,
               |    binaryField BINARY,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
               | TBLPROPERTIES('carbon.column.compressor'='gzip')
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarydata.csv'
               | INTO TABLE binaryTable
               | OPTIONS('header'='false')
             """.stripMargin)
        checkAnswer(sql("SELECT COUNT(*) FROM binaryTable"), Seq(Row(3)))
        val value = sql("SELECT * FROM binaryTable").collect()
        value.foreach { each =>
            assert(5 == each.length)
            assert(1 == each.getAs[Int](0) || 2 == each.getAs[Int](0) || 3 == each.getAs[Int](0))
            assert(".png".equals(each.getAs(2).toString.substring(1, 5)))
            assert("89504e470d0a1a0a0000000d4948445200000".equals(new String(each.getAs[Array[Byte]](3).slice(0, 37))))
        }
    }

    test("Test carbon.column.compressor=snappy") {
        sql("DROP TABLE IF EXISTS binaryTable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS binaryTable (
               |    id INT,
               |    label boolean,
               |    name STRING,
               |    binaryField BINARY,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
               | TBLPROPERTIES('carbon.column.compressor'='snappy')
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarydata.csv'
               | INTO TABLE binaryTable
               | OPTIONS('header'='false')
             """.stripMargin)
        checkAnswer(sql("SELECT COUNT(*) FROM binaryTable"), Seq(Row(3)))
        val value = sql("SELECT * FROM binaryTable").collect()
        value.foreach { each =>
            assert(5 == each.length)
            assert(1 == each.getAs[Int](0) || 2 == each.getAs[Int](0) || 3 == each.getAs[Int](0))
            assert(".png".equals(each.getAs(2).toString.substring(1, 5)))
            assert("89504e470d0a1a0a0000000d4948445200000".equals(new String(each.getAs[Array[Byte]](3).slice(0, 37))))
        }
    }

    test("Support filter other column in binary table") {
        sql("DROP TABLE IF EXISTS binaryTable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS binaryTable (
               |    id INT,
               |    label boolean,
               |    name STRING,
               |    binaryField BINARY,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
               | TBLPROPERTIES('carbon.column.compressor'='zstd')
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarydata.csv'
               | INTO TABLE binaryTable
               | OPTIONS('header'='false')
             """.stripMargin)
        checkAnswer(sql("SELECT COUNT(*) FROM binaryTable where id =1"), Seq(Row(1)))


        sql("insert into binaryTable values(1,true,'Bob','hello',false)")
        checkAnswer(sql("SELECT COUNT(*) FROM binaryTable where binaryField =cast('hello' as binary)"), Seq(Row(1)))
    }

    test("Test create table with buckets unsafe") {
        CarbonProperties.getInstance().addProperty(CarbonCommonConstants.ENABLE_UNSAFE_SORT, "true")
        sql("DROP TABLE IF EXISTS binaryTable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS binaryTable (
               |    id INT,
               |    label boolean,
               |    name STRING,
               |    binaryField BINARY,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
               | TBLPROPERTIES('BUCKETNUMBER'='4', 'BUCKETCOLUMNS'='binaryField')
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarydata.csv'
               | INTO TABLE binaryTable
               | OPTIONS('header'='false')
             """.stripMargin)

        CarbonProperties.getInstance().addProperty(CarbonCommonConstants.ENABLE_UNSAFE_SORT, "false")
        val table: CarbonTable = CarbonMetadata.getInstance().getCarbonTable("default", "binaryTable")
        if (table != null && table.getBucketingInfo("binarytable") != null) {
            assert(true)
        } else {
            assert(false, "Bucketing info does not exist")
        }
    }

    test("insert into for hive and carbon") {
        sql("DROP TABLE IF EXISTS hiveTable")
        sql("DROP TABLE IF EXISTS carbontable")
        sql("DROP TABLE IF EXISTS hiveTable2")
        sql("DROP TABLE IF EXISTS carbontable2")

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS hivetable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | row format delimited fields terminated by ','
             """.stripMargin)
        sql("insert into hivetable values(1,true,'Bob','binary',false)")
        sql("insert into hivetable values(2,false,'Xu','test',true)")
        sql("insert into hivetable select 2,false,'Xu',cast('carbon' as binary),true")
        val hiveResult = sql("SELECT * FROM hivetable")

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        sql("insert into carbontable values(1,true,'Bob','binary',false)")
        sql("insert into carbontable values(2,false,'Xu','test',true)")
        sql("insert into carbontable select 2,false,'Xu',cast('carbon' as binary),true")
        val carbonResult = sql("SELECT * FROM carbontable")
        checkAnswer(hiveResult, carbonResult)

        sql("CREATE TABLE hivetable2 AS SELECT * FROM carbontable")
        sql("CREATE TABLE carbontable2 AS SELECT * FROM hivetable")
        val carbonResult2 = sql("SELECT * FROM carbontable2")
        val hiveResult2 = sql("SELECT * FROM hivetable2")
        checkAnswer(hiveResult2, carbonResult2)
        checkAnswer(carbonResult, carbonResult2)
        checkAnswer(hiveResult, hiveResult2)
        assert(3 == carbonResult2.collect().length)
        assert(3 == hiveResult2.collect().length)

        sql("INSERT INTO hivetable2 SELECT * FROM carbontable")
        sql("INSERT INTO carbontable2 SELECT * FROM hivetable")
        val carbonResult3 = sql("SELECT * FROM carbontable2")
        val hiveResult3 = sql("SELECT * FROM hivetable2")
        checkAnswer(carbonResult3, hiveResult3)
        assert(6 == carbonResult3.collect().length)
        assert(6 == hiveResult3.collect().length)
    }

    test("Support filter for hive and carbon") {
        sql("DROP TABLE IF EXISTS hiveTable")
        sql("DROP TABLE IF EXISTS carbontable")

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS hivetable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | row format delimited fields terminated by ','
             """.stripMargin)
        sql("insert into hivetable values(1,true,'Bob','binary',false)")
        sql("insert into hivetable values(2,false,'Xu','test',true)")
        val hiveResult = sql("SELECT * FROM hivetable where binaryField=cast('binary' as binary)")

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        sql("insert into carbontable values(1,true,'Bob','binary',false)")
        sql("insert into carbontable values(2,false,'Xu','test',true)")
        val carbonResult = sql("SELECT * FROM carbontable where binaryField=cast('binary' as binary)")
        checkAnswer(hiveResult, carbonResult)
        assert(1 == carbonResult.collect().length)
        carbonResult.collect().foreach { each =>
            if (1 == each.get(0)) {
                assert("binary".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (2 == each.get(0)) {
                assert("test".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        // filter with non string
        val exception = intercept[Exception] {
            sql("SELECT * FROM carbontable where binaryField=binary").collect()
        }
        assert(exception.getMessage.contains("cannot resolve '`binary`' given input columns"))

        // filter with not equal
        val hiveResult3 = sql("SELECT * FROM hivetable where binaryField!=cast('binary' as binary)")
        val carbonResult3 = sql("SELECT * FROM carbontable where binaryField!=cast('binary' as binary)")
        checkAnswer(hiveResult3, carbonResult3)
        assert(1 == carbonResult3.collect().length)
        carbonResult3.collect().foreach { each =>
            assert(2 == each.get(0))
            assert("test".equals(new String(each.getAs[Array[Byte]](3))))
        }

        // filter with in
        val hiveResult4 = sql("SELECT * FROM hivetable where binaryField in (cast('binary' as binary))")
        val carbonResult4 = sql("SELECT * FROM carbontable where binaryField in (cast('binary' as binary))")
        checkAnswer(hiveResult4, carbonResult4)
        assert(1 == carbonResult4.collect().length)
        carbonResult4.collect().foreach { each =>
            assert(1 == each.get(0))
            assert("binary".equals(new String(each.getAs[Array[Byte]](3))))
        }

        // filter with not in
        val hiveResult5 = sql("SELECT * FROM hivetable where binaryField not in (cast('binary' as binary))")
        val carbonResult5 = sql("SELECT * FROM carbontable where binaryField not in (cast('binary' as binary))")
        checkAnswer(hiveResult5, carbonResult5)
        assert(1 == carbonResult5.collect().length)
        carbonResult5.collect().foreach { each =>
            assert(2 == each.get(0))
            assert("test".equals(new String(each.getAs[Array[Byte]](3))))
        }
    }

    test("Support update and delete ") {
        sql("DROP TABLE IF EXISTS carbontable")

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        sql("insert into carbontable values(1,true,'Bob','binary',false)")
        sql("insert into carbontable values(2,false,'Xu','test',true)")
        var carbonResult = sql("SELECT * FROM carbontable where binaryField=cast('binary' as binary)")
        assert(1 == carbonResult.collect().length)
        carbonResult.collect().foreach { each =>
            if (1 == each.get(0)) {
                assert("binary".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (2 == each.get(0)) {
                assert("test".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        // Update for binary in carbon
        sql("UPDATE carbontable SET (name) = ('David') WHERE id = 1").show()
        sql("UPDATE carbontable SET (binaryField) = ('carbon2') WHERE id = 1").show()

        carbonResult = sql("SELECT * FROM carbontable where binaryField=cast('binary' as binary)")
        carbonResult.collect().foreach { each =>
            if (1 == each.get(0)) {
                assert("carbon2".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (2 == each.get(0)) {
                assert("test".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        // test cast string to binary, binary to string
        val stringValue = sql("SELECT cast(binaryField as string) FROM carbontable WHERE id = 1").collect()
        stringValue.foreach { each =>
            assert("carbon2".equals(each.getAs(0)))
        }
        val binaryValue = sql("SELECT cast(name as binary) FROM carbontable WHERE id = 1").collect()
        binaryValue.foreach { each =>
            assert("David".equals(new String(each.getAs[Array[Byte]](0))))
        }

        // Test delete
        sql("DELETE FROM carbontable WHERE id = 2").show()

        carbonResult = sql("SELECT * FROM carbontable where binaryField=cast('binary' as binary)")
        carbonResult.collect().foreach { each =>
            if (1 == each.get(0)) {
                assert("carbon2".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }
    }

    test("Create table and load data with binary column for hive and carbon, CTAS and insert int hive table select from carbon table") {
        sql("DROP TABLE IF EXISTS hivetable")
        sql("DROP TABLE IF EXISTS hivetable2")
        sql("DROP TABLE IF EXISTS hivetable3")
        sql("DROP TABLE IF EXISTS carbontable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS hivetable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | row format delimited fields terminated by '|'
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarystringdata.csv'
               | INTO TABLE hivetable
             """.stripMargin)

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarystringdata.csv'
               | INTO TABLE carbontable
               | OPTIONS('header'='false','DELIMITER'='|','bad_records_action'='fail')
             """.stripMargin)

        val hiveResult = sql("SELECT * FROM hivetable")
        val carbonResult = sql("SELECT * FROM carbontable")
        checkAnswer(hiveResult, carbonResult)
        checkAnswer(sql("SELECT COUNT(*) FROM hivetable"), Seq(Row(3)))
        try {
            val carbonDF = carbonResult.collect()
            assert(3 == carbonDF.length)
            carbonDF.foreach { each =>
                assert(5 == each.length)

                assert(Integer.valueOf(each(0).toString) > 0)
                assert(each(1).toString.equalsIgnoreCase("false") || (each(1).toString.equalsIgnoreCase("true")))
                assert(each(2).toString.contains(".png"))

                val value = new String(each.getAs[Array[Byte]](3))
                assert("\u0001history\u0002".equals(value) || "\u0001biology\u0002".equals(value)
                        || "\u0001education\u0002".equals(value) || "".equals(value))
                assert(each(4).toString.equalsIgnoreCase("false") || (each(4).toString.equalsIgnoreCase("true")))
            }

            val df = hiveResult.collect()
            assert(3 == df.length)
            df.foreach { each =>
                assert(5 == each.length)

                assert(Integer.valueOf(each(0).toString) > 0)
                assert(each(1).toString.equalsIgnoreCase("false") || (each(1).toString.equalsIgnoreCase("true")))
                assert(each(2).toString.contains(".png"))


                val value = new String(each.getAs[Array[Byte]](3))
                assert("\u0001history\u0002".equals(value) || "\u0001biology\u0002".equals(value)
                        || "\u0001education\u0002".equals(value) || "".equals(value))
                assert(each(4).toString.equalsIgnoreCase("false") || (each(4).toString.equalsIgnoreCase("true")))
            }

            sql(
                s"""
                   | CREATE TABLE IF NOT EXISTS hivetable2 (
                   |    id int,
                   |    label boolean,
                   |    name string,
                   |    binaryField binary,
                   |    autoLabel boolean)
                   | row format delimited fields terminated by '|'
             """.stripMargin)
            sql("insert into hivetable2 select * from carbontable")
            sql("create table hivetable3 as select * from carbontable")
            val hiveResult2 = sql("SELECT * FROM hivetable2")
            val hiveResult3 = sql("SELECT * FROM hivetable3")
            checkAnswer(hiveResult, hiveResult2)
            checkAnswer(hiveResult2, hiveResult3)
        } catch {
            case e: Exception =>
                e.printStackTrace()
                assert(false)
        }
    }

    // TODO
    ignore("Create table and load data with binary column for hive and carbon, CTAS and insert int hive table select from carbon table, for null value") {
        sql("DROP TABLE IF EXISTS hivetable")
        sql("DROP TABLE IF EXISTS hivetable2")
        sql("DROP TABLE IF EXISTS hivetable3")
        sql("DROP TABLE IF EXISTS carbontable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS hivetable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | row format delimited fields terminated by '|'
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binaryStringNullData.csv'
               | INTO TABLE hivetable
             """.stripMargin)

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binaryStringNullData.csv'
               | INTO TABLE carbontable
               | OPTIONS('header'='false','DELIMITER'='|','bad_records_action'='fail')
             """.stripMargin)

        val hiveResult = sql("SELECT * FROM hivetable")
        val carbonResult = sql("SELECT * FROM carbontable")
        checkAnswer(hiveResult, carbonResult)
        checkAnswer(sql("SELECT COUNT(*) FROM hivetable"), Seq(Row(4)))
        try {
            val carbonDF = carbonResult.collect()
            assert(4 == carbonDF.length)
            carbonDF.foreach { each =>
                assert(5 == each.length)

                assert(Integer.valueOf(each(0).toString) > 0)
                assert(each(1).toString.equalsIgnoreCase("false") || (each(1).toString.equalsIgnoreCase("true")))
                assert(each(2).toString.contains(".png"))

                val value = new String(each.getAs[Array[Byte]](3))
                assert("\u0001history\u0002".equals(value) || "\u0001biology\u0002".equals(value)
                        || "\u0001education\u0002".equals(value) || "".equals(value))
                assert(each(4).toString.equalsIgnoreCase("false") || (each(4).toString.equalsIgnoreCase("true")))
            }

            val df = hiveResult.collect()
            assert(4 == df.length)
            df.foreach { each =>
                assert(5 == each.length)

                assert(Integer.valueOf(each(0).toString) > 0)
                assert(each(1).toString.equalsIgnoreCase("false") || (each(1).toString.equalsIgnoreCase("true")))
                assert(each(2).toString.contains(".png"))


                val value = new String(each.getAs[Array[Byte]](3))
                assert("\u0001history\u0002".equals(value) || "\u0001biology\u0002".equals(value)
                        || "\u0001education\u0002".equals(value) || "".equals(value))
                assert(each(4).toString.equalsIgnoreCase("false") || (each(4).toString.equalsIgnoreCase("true")))
            }

            sql(
                s"""
                   | CREATE TABLE IF NOT EXISTS hivetable2 (
                   |    id int,
                   |    label boolean,
                   |    name string,
                   |    binaryField binary,
                   |    autoLabel boolean)
                   | row format delimited fields terminated by '|'
             """.stripMargin)
            sql("insert into hivetable2 select * from carbontable")
            sql("create table hivetable3 as select * from carbontable")
            val hiveResult2 = sql("SELECT * FROM hivetable2")
            val hiveResult3 = sql("SELECT * FROM hivetable3")
            checkAnswer(hiveResult, hiveResult2)
            checkAnswer(hiveResult2, hiveResult3)
        } catch {
            case e: Exception =>
                e.printStackTrace()
                assert(false)
        }
    }

    test("insert into carbon as select from hive after hive load data") {
        sql("DROP TABLE IF EXISTS hiveTable")
        sql("DROP TABLE IF EXISTS carbontable")
        sql("DROP TABLE IF EXISTS hiveTable2")
        sql("DROP TABLE IF EXISTS carbontable2")

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS hivetable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | row format delimited fields terminated by '|'
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarystringdata.csv'
               | INTO TABLE hivetable
             """.stripMargin)

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        sql("insert into carbontable select * from hivetable")
        val carbonResult = sql("SELECT * FROM carbontable")
        val hiveResult = sql("SELECT * FROM hivetable")

        assert(3 == carbonResult.collect().length)
        assert(3 == hiveResult.collect().length)
        checkAnswer(hiveResult, carbonResult)
        carbonResult.collect().foreach { each =>
            if (2 == each.get(0)) {
                assert("\u0001history\u0002".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (1 == each.get(0)) {
                assert("\u0001education\u0002".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (3 == each.get(0)) {
                assert("\u0001biology\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        sql("CREATE TABLE hivetable2 AS SELECT * FROM carbontable")
        sql("CREATE TABLE carbontable2 STORED BY 'carbondata' AS SELECT * FROM hivetable")
        val carbonResult2 = sql("SELECT * FROM carbontable2")
        val hiveResult2 = sql("SELECT * FROM hivetable2")
        checkAnswer(hiveResult2, carbonResult2)
        checkAnswer(carbonResult, carbonResult2)
        checkAnswer(hiveResult, hiveResult2)
        assert(3 == carbonResult2.collect().length)
        assert(3 == hiveResult2.collect().length)

        sql("INSERT INTO hivetable2 SELECT * FROM carbontable")
        sql("INSERT INTO carbontable2 SELECT * FROM hivetable")
        val carbonResult3 = sql("SELECT * FROM carbontable2")
        val hiveResult3 = sql("SELECT * FROM hivetable2")
        checkAnswer(carbonResult3, hiveResult3)
        assert(6 == carbonResult3.collect().length)
        assert(6 == hiveResult3.collect().length)
    }

    test("compaction for binary") {
        CarbonProperties.getInstance()
                .addProperty(CarbonCommonConstants.ENABLE_AUTO_LOAD_MERGE, "false")
                .addProperty(CarbonCommonConstants.COMPACTION_SEGMENT_LEVEL_THRESHOLD,
                    CarbonCommonConstants.DEFAULT_SEGMENT_LEVEL_THRESHOLD)
        sql("DROP TABLE IF EXISTS carbontable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        for (i <- 0 until (3)) {
            sql(
                s"""
                   | LOAD DATA LOCAL INPATH '$resourcesPath/binarystringdata.csv'
                   | INTO TABLE carbontable
                   | OPTIONS('header'='false','DELIMITER'='|')
             """.stripMargin)
        }
        // 3 segments, no compaction
        var segments = sql("SHOW SEGMENTS FOR TABLE carbontable")
        var SegmentSequenceIds = segments.collect().map { each => (each.toSeq) (0) }
        assert(!SegmentSequenceIds.contains("0.1"))
        assert(SegmentSequenceIds.length == 3)
        for (i <- 0 until (3)) {
            sql("insert into carbontable values(1,true,'Bob','binary',false)")
        }

        // without auto compaction will not compact
        segments = sql("SHOW SEGMENTS FOR TABLE carbontable")
        SegmentSequenceIds = segments.collect().map { each => (each.toSeq) (0) }
        assert(!SegmentSequenceIds.contains("0.1"))
        assert(SegmentSequenceIds.length == 6)

        // minor compaction
        sql("alter table carbontable compact 'MINOR'")
        segments = sql("SHOW SEGMENTS FOR TABLE carbontable")
        SegmentSequenceIds = segments.collect().map { each => (each.toSeq) (0) }
        assert(SegmentSequenceIds.contains("0.1"))
        assert(!SegmentSequenceIds.contains("0.2"))
        assert(SegmentSequenceIds.length == 7)

        // major compaction
        sql("alter table carbontable compact 'major'")
        segments = sql("SHOW SEGMENTS FOR TABLE carbontable")
        SegmentSequenceIds = segments.collect().map { each => (each.toSeq) (0) }
        assert(SegmentSequenceIds.contains("0.2"))
        assert(SegmentSequenceIds.contains("0.1"))
        assert(SegmentSequenceIds.length == 8)

        // clean files
        segments = sql("CLEAN FILES FOR TABLE  carbontable")
        segments = sql("SHOW SEGMENTS FOR TABLE carbontable")
        SegmentSequenceIds = segments.collect().map { each => (each.toSeq) (0) }
        assert(SegmentSequenceIds.contains("0.2"))
        assert(!SegmentSequenceIds.contains("0.1"))
        assert(SegmentSequenceIds.length == 1)

        CarbonProperties.getInstance()
                .addProperty(CarbonCommonConstants.ENABLE_AUTO_LOAD_MERGE, "true")
        for (i <- 0 until (4)) {
            sql("insert into carbontable values(1,true,'Bob','binary',false)")
        }
        // auto compaction
        segments = sql("SHOW SEGMENTS FOR TABLE carbontable")
        SegmentSequenceIds = segments.collect().map { each => (each.toSeq) (0) }
        assert(SegmentSequenceIds.contains("6.1"))
        assert(!SegmentSequenceIds.contains("0.1"))
        assert(SegmentSequenceIds.contains("0.2"))
        assert(SegmentSequenceIds.length == 6)

        // check the data
        val carbonResult = sql("SELECT * FROM carbontable")
        carbonResult.collect().foreach { each =>
            if (2 == each.get(0)) {
                assert("\u0001history\u0002".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (1 == each.get(0)) {
                assert("\u0001education\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "binary".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (3 == each.get(0)) {
                assert("\u0001biology\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        CarbonProperties.getInstance()
                .addProperty(CarbonCommonConstants.ENABLE_AUTO_LOAD_MERGE,
                    CarbonCommonConstants.DEFAULT_ENABLE_AUTO_LOAD_MERGE)
    }

    test("alter table for binary") {
        sql("DROP TABLE IF EXISTS carbontable")
        sql("DROP TABLE IF EXISTS binarytable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string)
               | STORED BY 'carbondata'
             """.stripMargin)


        sql("insert into carbontable values(1,true,'Bob')")

        sql(
            s"""
               | alter table carbontable add columns (
               |    binaryField binary,
               |    autoLabel boolean)
               | TBLPROPERTIES('DEFAULT.VALUE.binaryField'='binary','DEFAULT.VALUE.autoLabel'='true')
            """.stripMargin)

        var carbonResult = sql("SELECT * FROM carbontable")
        carbonResult.collect().foreach { each =>
            if (1 == each.get(0)) {
                assert("binary".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarystringdata.csv'
               | INTO TABLE carbontable
               | OPTIONS('header'='false','DELIMITER'='|')
             """.stripMargin)


        sql("insert into carbontable values(1,true,'Bob','binary',false)")

        carbonResult = sql("SELECT * FROM carbontable")
        carbonResult.collect().foreach { each =>
            if (2 == each.get(0)) {
                assert("\u0001history\u0002".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (1 == each.get(0)) {
                assert("\u0001education\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "binary".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (3 == each.get(0)) {
                assert("\u0001biology\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        var result = sql("show tables")
        result.collect().foreach { each =>
            assert(!"binarytable".equalsIgnoreCase(each.getAs[String](1)))
        }

        // rename
        sql(
            s"""
               | alter table carbontable RENAME TO binarytable
            """.stripMargin)
        result = sql("show tables")
        assert(result.collect().exists { each =>
            "binarytable".equalsIgnoreCase(each.getAs[String](1))
        })

        // add columns after rename
        sql(
            s"""
               | alter table binarytable add columns (
               |    binaryField2 binary,
               |    autoLabel2 boolean)
               | TBLPROPERTIES('DEFAULT.VALUE.binaryField2'='binary','DEFAULT.VALUE.autoLabel2'='true')
            """.stripMargin)
        sql("insert into binarytable values(1,true,'Bob','binary',false,'binary',false)")

        carbonResult = sql("SELECT * FROM binarytable")
        carbonResult.collect().foreach { each =>
            if (2 == each.get(0)) {
                assert("\u0001history\u0002".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (1 == each.get(0)) {
                assert("\u0001education\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "binary".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (3 == each.get(0)) {
                assert(null == each.getAs[Array[Byte]](3)
                        || "\u0001biology\u0002".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        // drop columns after rename
        sql(
            s"""
               | alter table binarytable drop columns (
               |    binaryField2,
               |    autoLabel2)
            """.stripMargin)
        sql("insert into binarytable values(1,true,'Bob','binary',false)")

        carbonResult = sql("SELECT * FROM binarytable")
        carbonResult.collect().foreach { each =>
            if (2 == each.get(0)) {
                assert("\u0001history\u0002".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (1 == each.get(0)) {
                assert("\u0001education\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "binary".equals(new String(each.getAs[Array[Byte]](3))))
            } else if (3 == each.get(0)) {
                assert("\u0001biology\u0002".equals(new String(each.getAs[Array[Byte]](3)))
                        || "".equals(new String(each.getAs[Array[Byte]](3))))
            } else {
                assert(false)
            }
        }

        // change data type
        val e = intercept[Exception] {
            sql(s"alter table binarytable CHANGE binaryField binaryField3 STRING ")
        }
        assert(e.getMessage.contains("operation failed for default.binarytable: Alter table data type change operation failed: Given column binaryfield with data type BINARY cannot be modified. Only Int and Decimal data types are allowed for modification"))
    }

    ignore("Create table and load data with binary column for hive: test encode without \u0001") {
        sql("DROP TABLE IF EXISTS hivetable")
        sql("DROP TABLE IF EXISTS carbontable")
        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS hivetable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | row format delimited fields terminated by '|'
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarystringdata2.csv'
               | INTO TABLE hivetable
             """.stripMargin)

        sql(
            s"""
               | CREATE TABLE IF NOT EXISTS carbontable (
               |    id int,
               |    label boolean,
               |    name string,
               |    binaryField binary,
               |    autoLabel boolean)
               | STORED BY 'carbondata'
             """.stripMargin)
        sql(
            s"""
               | LOAD DATA LOCAL INPATH '$resourcesPath/binarystringdata2.csv'
               | INTO TABLE carbontable
               | OPTIONS('header'='false','DELIMITER'='|')
             """.stripMargin)

        val hiveResult = sql("SELECT * FROM hivetable")
        val carbonResult = sql("SELECT * FROM carbontable")
        // TODO
        checkAnswer(hiveResult, carbonResult)

        checkAnswer(sql("SELECT COUNT(*) FROM hivetable"), Seq(Row(3)))
        try {
            val carbonDF = carbonResult.collect()
            assert(3 == carbonDF.length)
            carbonDF.foreach { each =>
                assert(5 == each.length)

                assert(Integer.valueOf(each(0).toString) > 0)
                assert(each(1).toString.equalsIgnoreCase("false") || (each(1).toString.equalsIgnoreCase("true")))
                assert(each(2).toString.contains(".png"))

                val value = new String(each.getAs[Array[Byte]](3))
                // assert("\u0001history\u0002".equals(value) || "\u0001biology\u0002".equals(value) || "\u0001education\u0002".equals(value))
                assert(each(4).toString.equalsIgnoreCase("false") || (each(4).toString.equalsIgnoreCase("true")))
            }

            val df = hiveResult.collect()
            assert(3 == df.length)
            df.foreach { each =>
                assert(5 == each.length)

                assert(Integer.valueOf(each(0).toString) > 0)
                assert(each(1).toString.equalsIgnoreCase("false") || (each(1).toString.equalsIgnoreCase("true")))
                assert(each(2).toString.contains(".png"))


                val value = new String(each.getAs[Array[Byte]](3))
                // assert("\u0001history\u0002".equals(value) || "\u0001biology\u0002".equals(value) || "\u0001education\u0002".equals(value))
                assert(each(4).toString.equalsIgnoreCase("false") || (each(4).toString.equalsIgnoreCase("true")))
            }
        } catch {
            case e: Exception =>
                e.printStackTrace()
                assert(false)
        }
    }

    override def afterAll: Unit = {
        sql("DROP TABLE IF EXISTS binaryTable")
        sql("DROP TABLE IF EXISTS hiveTable")
    }
}