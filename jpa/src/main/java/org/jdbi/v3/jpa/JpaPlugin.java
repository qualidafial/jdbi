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
package org.jdbi.v3.jpa;

import com.google.auto.service.AutoService;
import org.jdbi.v3.Handle;
import org.jdbi.v3.spi.JdbiPlugin;

@AutoService(JdbiPlugin.class)
public class JpaPlugin implements JdbiPlugin {
    @Override
    public Handle customizeHandle(Handle handle) {
        handle.registerMapper(new JpaMapperFactory());
        return handle;
    }
}
