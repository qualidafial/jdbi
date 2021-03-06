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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jdbi.v3.tweak.Argument;
import org.jdbi.v3.tweak.NamedArgumentFinder;

/**
 * Binds all fields of a map as arguments.
 */
class MapArguments implements NamedArgumentFinder
{
    private final ArgumentRegistry argumentRegistry;
    private final StatementContext ctx;
    private final Map<String, ?> args;

    MapArguments(ArgumentRegistry argumentRegistry, StatementContext ctx, Map<String, ?> args)
    {
        this.argumentRegistry = argumentRegistry;
        this.ctx = ctx;
        this.args = args;
    }

    @Override
    public Optional<Argument> find(String name)
    {
        if (args.containsKey(name))
        {
            final Object argument = args.get(name);
            final Class<?> argumentClass =
                    argument == null ? Object.class : argument.getClass();
            return argumentRegistry.findArgumentFor(argumentClass, argument, ctx);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return new LinkedHashMap<>(args).toString();
    }
}
