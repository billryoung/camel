/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mongodb;

import java.util.Formatter;
import java.util.List;

import static java.util.Arrays.asList;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

import org.apache.camel.builder.RouteBuilder;
import org.bson.types.ObjectId;
import org.junit.Test;

public class MongoDbOperationsTest extends AbstractMongoDbTest {

    @Test
    public void testCountOperation() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:count", "irrelevantBody");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);

        // Insert a record and test that the endpoint now returns 1
        testCollection.insert((DBObject) JSON.parse("{a:60}"));
        result = template.requestBody("direct:count", "irrelevantBody");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should contain 1 record", 1L, result);
        testCollection.remove(new BasicDBObject());
        
        // test dynamicity
        dynamicCollection.insert((DBObject) JSON.parse("{a:60}"));
        result = template.requestBodyAndHeader("direct:count", "irrelevantBody", MongoDbConstants.COLLECTION, dynamicCollectionName);
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Dynamic collection should contain 1 record", 1L, result);
        
    }

    @Test
    public void testInsertString() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:insert", "{\"_id\":\"testInsertString\", \"scientist\":\"Einstein\"}");
        assertTrue(result instanceof WriteResult);
        DBObject b = testCollection.findOne("testInsertString");
        assertNotNull("No record with 'testInsertString' _id", b);
    }

    @Test
    public void testStoreOid() throws Exception {
        DBObject dbObject = new BasicDBObject();
        ObjectId oid = template.requestBody("direct:testStoreOidOnInsert", dbObject, ObjectId.class);
        assertEquals(dbObject.get("_id"), oid);
    }

    @Test
    public void testStoreOids() throws Exception {
        DBObject firstDbObject = new BasicDBObject();
        DBObject secondDbObject = new BasicDBObject();
        List<ObjectId> oids = template.requestBody("direct:testStoreOidOnInsert", asList(firstDbObject, secondDbObject), List.class);
        assertTrue(oids.contains(firstDbObject.get("_id")));
        assertTrue(oids.contains(secondDbObject.get("_id")));
    }

    @Test
    public void testSave() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        Object[] req = new Object[] {"{\"_id\":\"testSave1\", \"scientist\":\"Einstein\"}", "{\"_id\":\"testSave2\", \"scientist\":\"Copernicus\"}"};
        Object result = template.requestBody("direct:insert", req);
        assertTrue(result instanceof WriteResult);
        assertEquals("Number of records persisted must be 2", 2, testCollection.count());
        
        // Testing the save logic
        DBObject record1 = testCollection.findOne("testSave1");
        assertEquals("Scientist field of 'testSave1' must equal 'Einstein'", "Einstein", record1.get("scientist"));
        record1.put("scientist", "Darwin");
        
        result = template.requestBody("direct:save", record1);
        assertTrue(result instanceof WriteResult);
        
        record1 = testCollection.findOne("testSave1");
        assertEquals("Scientist field of 'testSave1' must equal 'Darwin' after save operation", "Darwin", record1.get("scientist"));

    }
    
    @Test
    public void testUpdate() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            Formatter f = new Formatter();
            if (i % 2 == 0) {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
            } else {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
            }
            f.close();
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());
        
        // Testing the update logic
        DBObject extraField = new BasicDBObject("extraField", true);
        assertEquals("Number of records with 'extraField' flag on must equal 50", 50L, testCollection.count(extraField));
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 0", 0, testCollection.count(new BasicDBObject("scientist", "Darwin")));

        DBObject updateObj = new BasicDBObject("$set", new BasicDBObject("scientist", "Darwin"));
        
        Object result = template.requestBodyAndHeader("direct:update", new Object[] {extraField, updateObj}, MongoDbConstants.MULTIUPDATE, true);
        assertTrue(result instanceof WriteResult);
        
        assertEquals("Number of records with 'scientist' field = Darwin on must equal 50 after update", 50, 
                testCollection.count(new BasicDBObject("scientist", "Darwin")));

    }
    
    @Test
    public void testRemove() throws Exception {
        // Prepare test
        assertEquals(0, testCollection.count());
        for (int i = 1; i <= 100; i++) {
            String body = null;
            Formatter f = new Formatter();
            if (i % 2 == 0) {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
            } else {
                body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\", \"extraField\": true}", i).toString();
            }
            f.close();
            template.requestBody("direct:insert", body);
        }
        assertEquals(100L, testCollection.count());
        
        // Testing the update logic
        DBObject extraField = new BasicDBObject("extraField", true);
        assertEquals("Number of records with 'extraField' flag on must equal 50", 50L, testCollection.count(extraField));
        
        Object result = template.requestBody("direct:remove", extraField);
        assertTrue(result instanceof WriteResult);
        
        assertEquals("Number of records with 'extraField' flag on must be 0 after remove", 0, 
                testCollection.count(extraField));

    }
    
    @Test
    public void testAggregate() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        pumpDataIntoTestCollection();

        // Repeat ten times, obtain 10 batches of 100 results each time
        Object result = template
            .requestBody("direct:aggregate",
                         "[{ $match : {$or : [{\"scientist\" : \"Darwin\"},{\"scientist\" : \"Einstein\"}]}},{ $group: { _id: \"$scientist\", count: { $sum: 1 }} } ]");
        assertTrue("Result is not of type List", result instanceof List);

        @SuppressWarnings("unchecked")
        List<DBObject> resultList = (List<DBObject>)result;
        assertListSize("Result does not contain 2 elements", resultList, 2);
        // TODO Add more asserts
    }
    
    @Test
    public void testDbStats() throws Exception {
        assertEquals(0, testCollection.count());
        Object result = template.requestBody("direct:getDbStats", "irrelevantBody");
        assertTrue("Result is not of type DBObject", result instanceof DBObject);
        assertTrue("The result should contain keys", ((DBObject) result).keySet().size() > 0);
    }
    
    @Test
    public void testColStats() throws Exception {
        assertEquals(0, testCollection.count());
        
        // Add some records to the collection (and do it via camel-mongodb)
        for (int i = 1; i <= 100; i++) {
            String body = null;
            Formatter f = new Formatter();
            body = f.format("{\"_id\":\"testSave%d\", \"scientist\":\"Einstein\"}", i).toString();
            f.close();
            template.requestBody("direct:insert", body);
        }
        
        Object result = template.requestBody("direct:getColStats", "irrelevantBody");
        assertTrue("Result is not of type DBObject", result instanceof DBObject);
        assertTrue("The result should contain keys", ((DBObject) result).keySet().size() > 0);
    }
    
    @Test
    public void testOperationHeader() throws Exception {
        // Test that the collection has 0 documents in it
        assertEquals(0, testCollection.count());
        
        // check that the count operation was invoked instead of the insert operation
        Object result = template.requestBodyAndHeader("direct:insert", "irrelevantBody", MongoDbConstants.OPERATION_HEADER, "count");
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);
        
        
        // check that the count operation was invoked instead of the insert operation
        result = template.requestBodyAndHeader("direct:insert", "irrelevantBody", MongoDbConstants.OPERATION_HEADER, MongoDbOperation.count);
        assertTrue("Result is not of type Long", result instanceof Long);
        assertEquals("Test collection should not contain any records", 0L, result);
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                
                from("direct:count").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=count&dynamicity=true");
                from("direct:insert").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&writeConcern=SAFE");
                from("direct:testStoreOidOnInsert").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=insert&writeConcern=SAFE").
                    setBody().header(MongoDbConstants.OID);
                from("direct:save").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=save&writeConcern=SAFE");
                from("direct:update").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=update&writeConcern=SAFE");
                from("direct:remove").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=remove&writeConcern=SAFE");
                from("direct:aggregate").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=aggregate&writeConcern=SAFE");
                from("direct:getDbStats").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=getDbStats");
                from("direct:getColStats").to("mongodb:myDb?database={{mongodb.testDb}}&collection={{mongodb.testCollection}}&operation=getColStats");


            }
        };
    }
}

