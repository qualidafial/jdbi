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

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;

import org.jdbi.v3.exceptions.UnableToCreateStatementException;
import org.jdbi.v3.exceptions.UnableToExecuteStatementException;
import org.jdbi.v3.tweak.Argument;
import org.jdbi.v3.tweak.ArgumentFactory;
import org.jdbi.v3.tweak.CollectorFactory;
import org.jdbi.v3.tweak.NamedArgumentFinder;
import org.jdbi.v3.tweak.RewrittenStatement;
import org.jdbi.v3.tweak.StatementBuilder;
import org.jdbi.v3.tweak.StatementCustomizer;
import org.jdbi.v3.tweak.StatementLocator;
import org.jdbi.v3.tweak.StatementRewriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides the common functions between <code>Query</code> and
 * <code>Update</code>. It defines most of the argument binding functions
 * used by its subclasses.
 */
public abstract class SQLStatement<SelfType extends SQLStatement<SelfType>> extends BaseStatement
{
    private static final Logger LOG = LoggerFactory.getLogger(SQLStatement.class);

    private final Binding          params;
    private final Handle           handle;
    private final String           sql;
    private final StatementBuilder statementBuilder;

    private StatementLocator  locator;
    private StatementRewriter rewriter;

    /**
     * This will be set on execution, not before
     */
    private       RewrittenStatement rewritten;
    private       PreparedStatement  stmt;
    private final TimingCollector    timingCollector;
    private final CollectorFactoryRegistry collectorFactoryRegistry;

    SQLStatement(Binding params,
                 StatementLocator locator,
                 StatementRewriter rewriter,
                 Handle handle,
                 StatementBuilder statementBuilder,
                 String sql,
                 ConcreteStatementContext ctx,
                 TimingCollector timingCollector,
                 Collection<StatementCustomizer> statementCustomizers,
                 ArgumentRegistry argumentRegistry,
                 CollectorFactoryRegistry collectorFactoryRegistry)
    {
        super(ctx, argumentRegistry);
        assert verifyOurNastyDowncastIsOkay();

        addCustomizers(statementCustomizers);

        this.statementBuilder = statementBuilder;
        this.rewriter = rewriter;
        this.handle = handle;
        this.sql = sql;
        this.timingCollector = timingCollector;
        this.params = params;
        this.locator = locator;
        this.collectorFactoryRegistry = collectorFactoryRegistry;

        ctx.setConnection(handle.getConnection());
        ctx.setRawSql(sql);
        ctx.setBinding(params);
    }

    CollectorFactoryRegistry getCollectorFactoryRegistry()
    {
        return collectorFactoryRegistry;
    }

    @SuppressWarnings("unchecked")
    public SelfType registerCollectorFactory(CollectorFactory collectorFactory) {
        this.collectorFactoryRegistry.register(collectorFactory);
        return (SelfType) this;
    }

    @SuppressWarnings("unchecked")
    public SelfType registerArgumentFactory(ArgumentFactory argumentFactory)
    {
        getArgumentRegistry().register(argumentFactory);
        return (SelfType) this;
    }

    /**
     * Override the statement locator used for this statement
     */
    @SuppressWarnings("unchecked")
    public SelfType setStatementLocator(StatementLocator locator) {
        this.locator = locator;
        return (SelfType) this;
    }

    /**
     * Override the statement rewriter used for this statement
     */
    @SuppressWarnings("unchecked")
    public SelfType setStatementRewriter(StatementRewriter rewriter) {
        this.rewriter = rewriter;
        return (SelfType) this;
    }

    @SuppressWarnings("unchecked")
    public SelfType setFetchDirection(final int value)
    {
        addStatementCustomizer(new StatementCustomizers.FetchDirectionStatementCustomizer(value));
        return (SelfType) this;
    }

    /**
     * Define a value on the {@link StatementContext}.
     *
     * @param key   Key to access this value from the StatementContext
     * @param value Value to setAttribute on the StatementContext
     *
     * @return this
     */
    @SuppressWarnings("unchecked")
    public SelfType define(String key, Object value)
    {
        getContext().setAttribute(key, value);
        return (SelfType) this;
    }

    /**
     * Adds all key/value pairs in the Map to the {@link StatementContext}.
     *
     * @param values containing key/value pairs.
     *
     * @return this
     */
    @SuppressWarnings("unchecked")
    public SelfType define(final Map<String, ?> values)
    {
        final StatementContext context = getContext();

        if (values != null) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                context.setAttribute(entry.getKey(), entry.getValue());
            }
        }
        return (SelfType) this;
    }

    /**
     * Provides a means for custom statement modification. Common cusotmizations
     * have their own methods, such as {@link Query#setMaxRows(int)}
     *
     * @param customizer instance to be used to cstomize a statement
     *
     * @return modified statement
     */
    @SuppressWarnings("unchecked")
    public SelfType addStatementCustomizer(StatementCustomizer customizer)
    {
        super.addCustomizer(customizer);
        return (SelfType) this;
    }

    private boolean verifyOurNastyDowncastIsOkay()
    {
        // Prevent bogus signatures like Update extends SQLStatement<Query>
        // SQLStatement's generic parameter must be supertype of getClass()
        return Types.findGenericParameter(getClass(), SQLStatement.class)
                .map(Types::getErasedType)
                .map(type -> type.isAssignableFrom(getClass()))
                .orElse(true); // subclass is raw type.. ¯\_(ツ)_/¯
    }

    protected StatementBuilder getStatementBuilder()
    {
        return statementBuilder;
    }

    protected StatementLocator getStatementLocator()
    {
        return this.locator;
    }

    protected StatementRewriter getRewriter()
    {
        return rewriter;
    }

    protected Binding getParams()
    {
        return params;
    }

    protected Handle getHandle()
    {
        return handle;
    }

    /**
     * The un-translated SQL used to create this statement
     */
    protected String getSql()
    {
        return sql;
    }

    protected Binding getParameters()
    {
        return params;
    }

    /**
     * Set the query timeout, in seconds, on the prepared statement
     *
     * @param seconds number of seconds before timing out
     *
     * @return the same instance
     */
    public SelfType setQueryTimeout(final int seconds)
    {
        return addStatementCustomizer(new StatementCustomizers.QueryTimeoutCustomizer(seconds));
    }

    /**
     * Close the handle when the statement is closed.
     */
    @SuppressWarnings("unchecked")
    public SelfType cleanupHandle()
    {
        super.addCleanable(Cleanables.forHandle(handle, TransactionState.ROLLBACK));
        return (SelfType) this;
    }

    /**
     * Force transaction state when the statement is cleaned up.
     */
    @SuppressWarnings("unchecked")
    public SelfType cleanupHandle(final TransactionState state)
    {
        super.addCleanable(Cleanables.forHandle(handle, state));
        return (SelfType) this;
    }


    /**
     * Used if you need to have some exotic parameter bound.
     *
     * @param position position to bindBinaryStream this argument, starting at 0
     * @param argument exotic argument factory
     *
     * @return the same Query instance
     */
    @SuppressWarnings("unchecked")
    public SelfType bind(int position, Argument argument)
    {
        getParams().addPositional(position, argument);
        return (SelfType) this;
    }

    /**
     * Used if you need to have some exotic parameter bound.
     *
     * @param name     name to bindBinaryStream this argument
     * @param argument exotic argument factory
     *
     * @return the same Query instance
     */
    @SuppressWarnings("unchecked")
    public SelfType bind(String name, Argument argument)
    {
        getParams().addNamed(name, argument);
        return (SelfType) this;
    }

    /**
     * Binds named parameters from JavaBean properties on o.
     *
     * @param o source of named parameter values to use as arguments
     *
     * @return modified statement
     */
    public SelfType bindFromProperties(Object o)
    {
        return bindNamedArgumentFinder(new BeanPropertyArguments(o, getContext(), getArgumentRegistry()));
    }

    /**
     * Binds named parameters from a map of String to Object instances
     *
     * @param args map where keys are matched to named parameters in order to bind arguments.
     *             Can be null, in this case, the binding has no effect.
     *
     * @return modified statement
     */
    @SuppressWarnings("unchecked")
    public SelfType bindFromMap(Map<String, ?> args)
    {
        if (args != null) {
            return bindNamedArgumentFinder(new MapArguments(getArgumentRegistry(), getContext(), args));
        }
        else {
            return (SelfType) this;
        }
    }

    /**
     * Binds a new {@link NamedArgumentFinder}.
     *
     * @param namedArgumentFinder A NamedArgumentFinder to bind. Can be null.
     */
    @SuppressWarnings("unchecked")
    public SelfType bindNamedArgumentFinder(final NamedArgumentFinder namedArgumentFinder)
    {
        if (namedArgumentFinder != null) {
            getParams().addNamedArgumentFinder(namedArgumentFinder);
        }

        return (SelfType) this;
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Character value)
    {
        return bind(position, toArgument(Character.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Character value)
    {
        return bind(name, toArgument(Character.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, String value)
    {
        return bind(position, toArgument(String.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, String value)
    {
        return bind(name, toArgument(String.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, int value)
    {
        return bind(position, toArgument(int.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Integer value)
    {
        return bind(position, toArgument(Integer.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, int value)
    {
        return bind(name, toArgument(int.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Integer value)
    {
        return bind(name, toArgument(Integer.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, char value)
    {
        return bind(position, toArgument(char.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, char value)
    {
        return bind(name, toArgument(char.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     * @param length   how long is the stream being bound?
     *
     * @return the same Query instance
     */
    public final SelfType bindASCIIStream(int position, InputStream value, int length)
    {
        return bind(position, new InputStreamArgument(value, length, true));
    }

    /**
     * Bind an argument by name
     *
     * @param name   token name to bind the parameter to
     * @param value  to bind
     * @param length bytes to read from value
     *
     * @return the same Query instance
     */
    public final SelfType bindASCIIStream(String name, InputStream value, int length)
    {
        return bind(name, new InputStreamArgument(value, length, true));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, BigDecimal value)
    {
        return bind(position, toArgument(BigDecimal.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, BigDecimal value)
    {
        return bind(name, toArgument(BigDecimal.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bindBinaryStream(int position, InputStream value, int length)
    {
        return bind(position, new InputStreamArgument(value, length, false));
    }

    /**
     * Bind an argument by name
     *
     * @param name   token name to bind the parameter to
     * @param value  to bind
     * @param length bytes to read from value
     *
     * @return the same Query instance
     */
    public final SelfType bindBinaryStream(String name, InputStream value, int length)
    {
        return bind(name, new InputStreamArgument(value, length, false));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Blob value)
    {
        return bind(position, toArgument(Blob.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Blob value)
    {
        return bind(name, toArgument(Blob.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, boolean value)
    {
        return bind(position, toArgument(boolean.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Boolean value)
    {
        return bind(position, toArgument(Boolean.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, boolean value)
    {
        return bind(name, toArgument(boolean.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Boolean value)
    {
        return bind(name, toArgument(Boolean.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, byte value)
    {
        return bind(position, toArgument(byte.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Byte value)
    {
        return bind(position, toArgument(Byte.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, byte value)
    {
        return bind(name, toArgument(byte.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Byte value)
    {
        return bind(name, toArgument(Byte.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, byte[] value)
    {
        return bind(position, toArgument(byte[].class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, byte[] value)
    {
        return bind(name, toArgument(byte[].class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     * @param length   number of characters to read
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Reader value, int length)
    {

        return bind(position, new CharacterStreamArgument(value, length));
    }

    /**
     * Bind an argument by name
     *
     * @param name   token name to bind the parameter to
     * @param value  to bind
     * @param length number of characters to read
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Reader value, int length)
    {
        return bind(name, new CharacterStreamArgument(value, length));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Clob value)
    {
        return bind(position, toArgument(Clob.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Clob value)
    {
        return bind(name, toArgument(Clob.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, java.sql.Date value)
    {
        return bind(position, toArgument(java.sql.Date.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, java.sql.Date value)
    {
        return bind(name, toArgument(java.sql.Date.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, java.util.Date value)
    {
        return bind(position, toArgument(java.util.Date.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, java.util.Date value)
    {
        return bind(name, toArgument(java.util.Date.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, double value)
    {
        return bind(position, toArgument(double.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Double value)
    {
        return bind(position, toArgument(Double.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, double value)
    {
        return bind(name, toArgument(double.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Double value)
    {
        return bind(name, toArgument(Double.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, float value)
    {
        return bind(position, toArgument(float.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Float value)
    {
        return bind(position, toArgument(Float.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, float value)
    {
        return bind(name, toArgument(float.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Float value)
    {
        return bind(name, toArgument(Float.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, long value)
    {
        return bind(position, toArgument(long.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Long value)
    {
        return bind(position, toArgument(Long.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, long value)
    {
        return bind(name, toArgument(long.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Long value)
    {
        return bind(name, toArgument(Long.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Short value)
    {
        return bind(position, toArgument(Short.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, short value)
    {
        return bind(position, toArgument(short.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, short value)
    {
        return bind(name, toArgument(short.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Short value)
    {
        return bind(name, toArgument(short.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Object value)
    {
        return bind(position, toArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Object value)
    {
        return bind(name, toArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Time value)
    {
        return bind(position, toArgument(Time.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Time value)
    {
        return bind(name, toArgument(Time.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Timestamp value)
    {
        return bind(position, toArgument(Timestamp.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Timestamp value)
    {
        return bind(name, toArgument(Timestamp.class, value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the parameter at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, URL value)
    {
        return bind(position, toArgument(URL.class, value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, URL value)
    {
        return bind(name, toArgument(URL.class, value));
    }

    /**
     * Bind an argument dynamically by the type passed in.
     *
     * @param position     position to bind the parameter at, starting at 0
     * @param value        to bind
     * @param argumentType type for value argument
     *
     * @return the same Query instance
     */
    public final SelfType bindByType(int position, Object value, Type argumentType)
    {
        return bind(position, toArgument(argumentType, value));
    }

    /**
     * Bind an argument dynamically by the generic type passed in.
     *
     * @param position     position to bind the parameter at, starting at 0
     * @param value        to bind
     * @param argumentType type token for value argument
     *
     * @return the same Query instance
     */
    public final SelfType bindByType(int position, Object value, GenericType<?> argumentType)
    {
        return bindByType(position, value, argumentType.getType());
    }

    /**
     * Bind an argument dynamically by the type passed in.
     *
     * @param name         token name to bind the parameter to
     * @param value        to bind
     * @param argumentType type for value argument
     *
     * @return the same Query instance
     */
    public final SelfType bindByType(String name, Object value, Type argumentType)
    {
        return bind(name, toArgument(argumentType, value));
    }

    /**
     * Bind an argument dynamically by the generic type passed in.
     *
     * @param name         token name to bind the parameter to
     * @param value        to bind
     * @param argumentType type token for value argument
     *
     * @return the same Query instance
     */
    public final SelfType bindByType(String name, Object value, GenericType<?> argumentType)
    {
        return bindByType(name, value, argumentType.getType());
    }

    private Argument toArgument(Object value) {
        return toArgument(value == null ? Object.class : value.getClass(), value);
    }

    private Argument toArgument(Type type, Object value) {
        return getArgumentRegistry().findArgumentFor(type, value, getContext())
                .orElseThrow(() -> new UnsupportedOperationException("No argument factory registered for '" + value + "' of type " + type));
    }

    /**
     * Bind NULL to be set for a given argument.
     *
     * @param name    Named parameter to bind to
     * @param sqlType The sqlType must be set and is a value from <code>java.sql.Types</code>
     *
     * @return the same statement instance
     */
    public final SelfType bindNull(String name, int sqlType)
    {
        return bind(name, new NullArgument(sqlType));
    }

    /**
     * Bind NULL to be set for a given argument.
     *
     * @param position position to bind NULL to, starting at 0
     * @param sqlType  The sqlType must be set and is a value from <code>java.sql.Types</code>
     *
     * @return the same statement instance
     */
    public final SelfType bindNull(int position, int sqlType)
    {
        return bind(position, new NullArgument(sqlType));
    }

    /**
     * Bind a value using a specific type from <code>java.sql.Types</code> via
     * PreparedStatement#setObject(int, Object, int)
     *
     * @param name    Named parameter to bind at
     * @param value   Value to bind
     * @param sqlType The sqlType from java.sql.Types
     *
     * @return self
     */
    public final SelfType bindBySqlType(String name, Object value, int sqlType)
    {
        return bind(name, new SqlTypeArgument(value, sqlType));
    }

    /**
     * Bind a value using a specific type from <code>java.sql.Types</code> via
     * PreparedStatement#setObject(int, Object, int)
     *
     * @param position position to bind NULL to, starting at 0
     * @param value    Value to bind
     * @param sqlType  The sqlType from java.sql.Types
     *
     * @return self
     */
    public final SelfType bindBySqlType(int position, Object value, int sqlType)
    {
        return bind(position, new SqlTypeArgument(value, sqlType));
    }

    private String wrapLookup(String sql)
    {
        try {
            return locator.locate(sql, this.getContext());
        }
        catch (Exception e) {
            throw new UnableToCreateStatementException("Exception thrown while looking for statement", e, getContext());
        }
    }

    protected PreparedStatement internalExecute()
    {
        final String located_sql = wrapLookup(sql);
        getConcreteContext().setLocatedSql(located_sql);
        rewritten = rewriter.rewrite(located_sql, getParameters(), getContext());
        getConcreteContext().setRewrittenSql(rewritten.getSql());
        try {
            if (getClass().isAssignableFrom(Call.class)) {
                stmt = statementBuilder.createCall(handle.getConnection(), rewritten.getSql(), getContext());
            }
            else {
                stmt = statementBuilder.create(handle.getConnection(), rewritten.getSql(), getContext());
            }
        }
        catch (SQLException e) {
            throw new UnableToCreateStatementException(e, getContext());
        }

        // The statement builder might (or might not) clean up the statement when called. E.g. the
        // caching statement builder relies on the statement *not* being closed.
        addCleanable(new Cleanables.StatementBuilderCleanable(statementBuilder, handle.getConnection(), sql, stmt));

        getConcreteContext().setStatement(stmt);

        try {
            rewritten.bind(getParameters(), stmt);
        }
        catch (SQLException e) {
            throw new UnableToExecuteStatementException("Unable to bind parameters to query", e, getContext());
        }

        beforeExecution(stmt);

        try {
            final long start = System.nanoTime();
            stmt.execute();
            final long elapsedTime = System.nanoTime() - start;
            LOG.trace("Execute SQL \"{}\" in {}ms", rewritten.getSql(), elapsedTime / 1000000L);
            timingCollector.collect(elapsedTime, getContext());
        }
        catch (SQLException e) {
            try {
                stmt.close();
            } catch (SQLException e1) {
                e.addSuppressed(e1);
            }
            throw new UnableToExecuteStatementException(e, getContext());
        }

        afterExecution(stmt);

        return stmt;
    }

    protected TimingCollector getTimingCollector()
    {
        return timingCollector;
    }
}
