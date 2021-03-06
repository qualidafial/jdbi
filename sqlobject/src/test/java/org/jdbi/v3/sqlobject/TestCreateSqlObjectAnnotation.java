/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.sqlobject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;

import org.jdbi.v3.H2DatabaseRule;
import org.jdbi.v3.Handle;
import org.jdbi.v3.Something;
import org.jdbi.v3.sqlobject.exceptions.UnableToCreateSqlObjectException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestCreateSqlObjectAnnotation
{
    @Rule
    public H2DatabaseRule db = new H2DatabaseRule();

    private Handle handle;

    @Before
    public void setUp() throws Exception
    {
        db.getDbi().registerMapper(new SomethingMapper());
        handle = db.getSharedHandle();
        handle.registerMapper(new SomethingMapper());
    }


    @Test
    public void testSimpleCreate() throws Exception
    {
        Foo foo = SqlObjectBuilder.attach(handle, Foo.class);
        foo.insert(1, "Stephane");
        Something s = foo.createBar().findById(1);
        assertThat(s, equalTo(new Something(1, "Stephane")));
    }

    @Test
    public void testInsertAndFind() throws Exception
    {
        Foo foo = SqlObjectBuilder.attach(handle, Foo.class);
        Something s = foo.insertAndFind(1, "Stephane");
        assertThat(s, equalTo(new Something(1, "Stephane")));
    }

    @Test
    public void testTransactionPropagates() throws Exception
    {
        Foo foo = SqlObjectBuilder.onDemand(db.getDbi(), Foo.class);

        try {
            foo.insertAndFail(1, "Jeff");
            fail("should have raised an exception");
        }
        catch (Exception e){}
        Something n = foo.createBar().findById(1);
        assertThat(n, nullValue());
    }

    public static abstract class Foo
    {
        @CreateSqlObject
        public abstract Bar createBar();

        @SqlUpdate("insert into something (id, name) values (:id, :name)")
        public abstract int insert(@Bind("id") int id, @Bind("name") String name);

        @Transaction
        public Something insertAndFind(int id, String name) {
            insert(id, name);
            return createBar().findById(id);
        }

        @Transaction
        public Something insertAndFail(int id, String name) {
            insert(id, name);
            return createBar().explode();
        }
    }

    public static abstract class Bar
    {
        @SqlQuery("select id, name from something where id = :id")
        public abstract Something findById(@Bind("id") int id);

        public Something explode()
        {
            throw new RuntimeException();
        }

    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testMeaningfulExceptionWhenWrongReturnTypeOfSqlUpdate() throws Exception {
        expectedException.expect(UnableToCreateSqlObjectException.class);
        expectedException.expectMessage("BogusSqlUpdateDao.getNames method is annotated with @SqlUpdate " +
                "so should return void or Number but is returning: java.util.List<java.lang.String>");

        db.getDbi().open(BogusSqlUpdateDao.class);
    }

    public interface BogusSqlUpdateDao {
        @SqlUpdate("select name from something")
        List<String> getNames();
    }

    @Test
    public void testMeaningfulExceptionWhenWrongReturnTypeOfSqlBatch() throws Exception {
        expectedException.expect(UnableToCreateSqlObjectException.class);
        expectedException.expectMessage("BogusSqlBatchDao.getNames method is annotated with @SqlBatch " +
                "so should return void or int[] but is returning: int");

        db.getDbi().open(BogusSqlBatchDao.class);
    }

    public interface BogusSqlBatchDao {
        @SqlBatch("insert into table (a) values (:a)")
        int getNames(@Bind("a") String a);
    }
}
