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
package org.jdbi.v3.util.bean;

/**
 * Strategy for mapping Java bean property and field names
 * to SQL column names.
 */
// TODO 3: Is this the right pattern?
public interface ColumnNameMappingStrategy {
    /**
     * Given the Java name and SQL column name,
     * return true if they are logically equivalent.
     */
    boolean nameMatches(String propertyName, String sqlColumnName);
}
