/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.jdbc.client;

import com.facebook.presto.jdbc.spi.type.TypeSignature;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.validation.constraints.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.facebook.presto.jdbc.spi.type.StandardTypes.ARRAY;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.BIGINT;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.BOOLEAN;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.DATE;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.DOUBLE;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.INTERVAL_DAY_TO_SECOND;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.INTERVAL_YEAR_TO_MONTH;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.JSON;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.MAP;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.ROW;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.TIME;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.TIMESTAMP;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.TIMESTAMP_WITH_TIME_ZONE;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.TIME_WITH_TIME_ZONE;
import static com.facebook.presto.jdbc.spi.type.StandardTypes.VARCHAR;
import static com.facebook.presto.jdbc.spi.type.TypeSignature.parseTypeSignature;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.unmodifiableIterable;
import static com.google.common.io.BaseEncoding.base64;
import static java.util.Collections.unmodifiableList;

@Immutable
public class QueryResults
{
    private final String id;
    private final URI infoUri;
    private final URI partialCancelUri;
    private final URI nextUri;
    private final List<Column> columns;
    private final Iterable<List<Object>> data;
    private final StatementStats stats;
    private final QueryError error;
    private final String updateType;
    private final Long updateCount;

    @JsonCreator
    public QueryResults(
            @JsonProperty("id") String id,
            @JsonProperty("infoUri") URI infoUri,
            @JsonProperty("partialCancelUri") URI partialCancelUri,
            @JsonProperty("nextUri") URI nextUri,
            @JsonProperty("columns") List<Column> columns,
            @JsonProperty("data") List<List<Object>> data,
            @JsonProperty("stats") StatementStats stats,
            @JsonProperty("error") QueryError error,
            @JsonProperty("updateType") String updateType,
            @JsonProperty("updateCount") Long updateCount)
    {
        this(id, infoUri, partialCancelUri, nextUri, columns, fixData(columns, data), stats, error, updateType, updateCount);
    }

    public QueryResults(
            String id,
            URI infoUri,
            URI partialCancelUri,
            URI nextUri,
            List<Column> columns,
            Iterable<List<Object>> data,
            StatementStats stats,
            QueryError error,
            String updateType,
            Long updateCount)
    {
        this.id = checkNotNull(id, "id is null");
        this.infoUri = checkNotNull(infoUri, "infoUri is null");
        this.partialCancelUri = partialCancelUri;
        this.nextUri = nextUri;
        this.columns = (columns != null) ? ImmutableList.copyOf(columns) : null;
        this.data = (data != null) ? unmodifiableIterable(data) : null;
        this.stats = checkNotNull(stats, "stats is null");
        this.error = error;
        this.updateType = updateType;
        this.updateCount = updateCount;
    }

    @NotNull
    @JsonProperty
    public String getId()
    {
        return id;
    }

    @NotNull
    @JsonProperty
    public URI getInfoUri()
    {
        return infoUri;
    }

    @Nullable
    @JsonProperty
    public URI getPartialCancelUri()
    {
        return partialCancelUri;
    }

    @Nullable
    @JsonProperty
    public URI getNextUri()
    {
        return nextUri;
    }

    @Nullable
    @JsonProperty
    public List<Column> getColumns()
    {
        return columns;
    }

    @Nullable
    @JsonProperty
    public Iterable<List<Object>> getData()
    {
        return data;
    }

    @NotNull
    @JsonProperty
    public StatementStats getStats()
    {
        return stats;
    }

    @Nullable
    @JsonProperty
    public QueryError getError()
    {
        return error;
    }

    @Nullable
    @JsonProperty
    public String getUpdateType()
    {
        return updateType;
    }

    @Nullable
    @JsonProperty
    public Long getUpdateCount()
    {
        return updateCount;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("id", id)
                .add("infoUri", infoUri)
                .add("partialCancelUri", partialCancelUri)
                .add("nextUri", nextUri)
                .add("columns", columns)
                .add("hasData", data != null)
                .add("stats", stats)
                .add("error", error)
                .add("updateType", updateType)
                .add("updateCount", updateCount)
                .toString();
    }

    private static Iterable<List<Object>> fixData(List<Column> columns, List<List<Object>> data)
    {
        if (data == null) {
            return null;
        }
        checkNotNull(columns, "columns is null");
        ImmutableList.Builder<List<Object>> rows = ImmutableList.builder();
        for (List<Object> row : data) {
            checkArgument(row.size() == columns.size(), "row/column size mismatch");
            List<Object> newRow = new ArrayList<Object>();
            for (int i = 0; i < row.size(); i++) {
                newRow.add(fixValue(columns.get(i).getType(), row.get(i)));
            }
            rows.add(unmodifiableList(newRow)); // allow nulls in list
        }
        return rows.build();
    }

    /**
     * Force values coming from Jackson to have the expected object type.
     */
    private static Object fixValue(String type, Object value)
    {
        if (value == null) {
            return null;
        }
        TypeSignature signature = parseTypeSignature(type);
        if (signature.getBase().equals(ARRAY)) {
            List<Object> fixedValue = new ArrayList<Object>();
            for (Object object : List.class.cast(value)) {
                fixedValue.add(fixValue(signature.getParameters().get(0).toString(), object));
            }
            return fixedValue;
        }
        if (signature.getBase().equals(MAP)) {
            String keyType = signature.getParameters().get(0).toString();
            String valueType = signature.getParameters().get(1).toString();
            Map<Object, Object> fixedValue = new HashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : (Set<Map.Entry<?, ?>>) Map.class.cast(value).entrySet()) {
                fixedValue.put(fixValue(keyType, entry.getKey()), fixValue(valueType, entry.getValue()));
            }
            return fixedValue;
        }
        if (signature.getBase().equals(ROW)) {
            Map<String, Object> fixedValue = new LinkedHashMap<String, Object>();
            List<Object> listValue = List.class.cast(value);
            checkArgument(listValue.size() == signature.getLiteralParameters().size(), "Mismatched data values and row type");
            for (int i = 0; i < listValue.size(); i++) {
                String key = (String) signature.getLiteralParameters().get(i);
                fixedValue.put(key, fixValue(signature.getParameters().get(i).toString(), listValue.get(i)));
            }
            return fixedValue;
        }
        if (type.equals(BIGINT)) {
            if (value instanceof String) {
                return Long.parseLong((String) value);
            }
            return ((Number) value).longValue();
        }
        else if (type.equals(DOUBLE)) {
            if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
            return ((Number) value).doubleValue();
        }
        else if (type.equals(BOOLEAN)) {
            if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
            return Boolean.class.cast(value);
        }
        else if (type.equals(VARCHAR) ||
                type.equals(JSON) ||
                type.equals(TIME) ||
                type.equals(TIME_WITH_TIME_ZONE) ||
                type.equals(TIMESTAMP) ||
                type.equals(TIMESTAMP_WITH_TIME_ZONE) ||
                type.equals(DATE) ||
                type.equals(INTERVAL_YEAR_TO_MONTH) ||
                type.equals(INTERVAL_DAY_TO_SECOND)) {
            return String.class.cast(value);
        }
        else {
            // for now we assume that only the explicit types above are passed
            // as a plain text and everything else is base64 encoded binary
            if (value instanceof String) {
                return base64().decode((String) value);
            }
            return value;
        }
    }
}
