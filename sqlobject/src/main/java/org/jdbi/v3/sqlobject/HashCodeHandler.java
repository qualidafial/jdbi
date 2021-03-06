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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.sf.cglib.proxy.MethodProxy;

class HashCodeHandler implements Handler
{
    static Map<Method, Handler> handler()
    {
        try
        {
            Map<Method, Handler> handler = new HashMap<>();
            handler.put(Object.class.getMethod("hashCode"), new HashCodeHandler());
            return handler;
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalStateException("JVM error");
        }
    }

    @Override
    public Object invoke(HandleDing h, Object target, Object[] args, MethodProxy mp)
    {
        return System.identityHashCode(target);
    }
}
