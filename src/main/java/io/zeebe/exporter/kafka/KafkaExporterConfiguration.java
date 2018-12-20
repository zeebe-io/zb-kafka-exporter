/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
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
package io.zeebe.exporter.kafka;

import io.zeebe.exporter.record.Record;
import io.zeebe.util.ByteValue;
import io.zeebe.util.DurationUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.record.CompressionType;

public class KafkaExporterConfiguration {
  private static final String CLIENT_ID_FORMAT = "zb-kafka-exporter-%s";

  private final RecordSerializer keySerializer = new RecordSerializer();
  private final RecordSerializer valueSerializer = new RecordSerializer();

  public String topic;
  public int maxInFlightRecords = 1_000;
  public String awaitInFlightRecordTimeout = "5s";
  public ProducerConfiguration producer = new ProducerConfiguration();

  public Producer<Record, Record> newProducer(String exporterId) {
    final Map<String, Object> configs = producer.getConfigs(exporterId);

    keySerializer.configure(configs, true);
    valueSerializer.configure(configs, false);

    return new KafkaProducer<>(configs, keySerializer, valueSerializer);
  }

  public class ProducerConfiguration {
    public String batchSize = "128K";
    public String batchLinger = "100ms";
    public String clientId;
    public String closeTimeout = "10s";
    public String compressionType = CompressionType.SNAPPY.name;
    public int maxConcurrentRequests = 3;
    public String requestTimeout = "10s";
    public List<String> servers = Collections.singletonList("localhost:9092");

    public Map<String, String> config = new HashMap<>();

    private Map<String, Object> getConfigs(String exporterId) {
      final Map<String, Object> configs = new HashMap<>();
      String clientId = this.clientId;

      if (config != null) {
        configs.putAll(config);
      }

      if (clientId == null) {
        clientId = String.format(CLIENT_ID_FORMAT, exporterId);
      }

      configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
      configs.put(ProducerConfig.BATCH_SIZE_CONFIG, (int) new ByteValue(batchSize).toBytes());
      configs.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
      configs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
      configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
      configs.put(
          ProducerConfig.LINGER_MS_CONFIG, (int) DurationUtil.parse(batchLinger).toMillis());
      configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxConcurrentRequests);
      configs.put(
          ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
          (int) DurationUtil.parse(requestTimeout).toMillis());

      // configured such that we keep retrying a record "forever", even though single requests might
      // timeout and be retried
      configs.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Integer.MAX_VALUE);

      return configs;
    }
  }
}