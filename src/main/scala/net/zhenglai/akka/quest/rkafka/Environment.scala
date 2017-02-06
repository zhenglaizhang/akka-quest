package net.zhenglai.akka.quest.rkafka

import akka.actor.ActorSystem
import akka.kafka.{ ConsumerSettings, ProducerSettings }
import akka.stream.ActorMaterializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer }

trait Environment {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers("localhost:9092")

  val consumerSettings =
    ConsumerSettings(system, new ByteArrayDeserializer, new StringDeserializer)
      .withBootstrapServers("localhost:9092")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  //      .withClientId("cid1")
  //.withGroupId("egid1")
  // group id for the consumer, note that offsets are always committed for a given consumer group
}
