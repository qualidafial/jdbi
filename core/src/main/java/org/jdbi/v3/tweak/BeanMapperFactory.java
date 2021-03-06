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
package org.jdbi.v3.tweak;

import static org.jdbi.v3.Types.getErasedType;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jdbi.v3.BeanMapper;
import org.jdbi.v3.ResultSetMapperFactory;
import org.jdbi.v3.StatementContext;

public class BeanMapperFactory implements ResultSetMapperFactory
{
    private final List<Class<?>> beanClasses;

    public BeanMapperFactory(Class<?>... beanClasses) {
        this(Arrays.asList(beanClasses));
    }

    public BeanMapperFactory(List<Class<?>> beanClasses) {
        this.beanClasses = new ArrayList<>(beanClasses);
    }

    @Override
    public Optional<ResultSetMapper<?>> build(Type type, StatementContext ctx) {
        return Optional.of(getErasedType(type))
                .filter(beanClasses::contains)
                .map(BeanMapper::new);
    }
}
