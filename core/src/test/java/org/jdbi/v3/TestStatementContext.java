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
package org.jdbi.v3;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;

public class TestStatementContext
{
    @Rule
    public H2DatabaseRule db = new H2DatabaseRule();

    @Test
    public void testFoo() throws Exception
    {
        Handle h = db.openHandle();
        h.setStatementLocator((name, ctx) ->
                name.replaceAll("<table>", String.valueOf(ctx.getAttribute("table"))));
        final int inserted = h.createStatement("insert into <table> (id, name) values (:id, :name)")
                .bind("id", 7)
                .bind("name", "Martin")
                .define("table", "something")
                .execute();
        assertEquals(1, inserted);
    }
}
