package net.zhenglai.akka.quest.rkafka

import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.{ ProducerMessage, Subscriptions }
import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.stream.scaladsl.{ Sink, Source }
import org.apache.kafka.clients.producer.ProducerRecord

// connecting producer and consumer
object TransformerMain extends App with Environment {
  //  prepareTopic1

  //  atLeastOnce

  val done = Consumer
    .committableSource(
      consumerSettings.withGroupId("gid11"),
      Subscriptions.topics("topic2")
    )
    .map { msg =>
      ProducerMessage.Message(
        new ProducerRecord[Array[Byte], String]("topic3", msg.record.value),
        msg.committableOffset
      )
    }
    .via(Producer.flow(producerSettings))
    .map(_.message.passThrough)
    .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first))((offset, elem) => offset.updated(elem))
    .mapAsync(3)(_.commitScaladsl())
    .runWith(Sink.ignore)

  private def atLeastOnce = {
    Consumer.committableSource(
      consumerSettings.withGroupId("gid10"),
      Subscriptions.topics("topic1")
    )
      .map { msg =>
        println(s"topic1 -> topic2: $msg")
        ProducerMessage.Message(
          new ProducerRecord[Array[Byte], String]("topic2", msg.record.value),
          msg.committableOffset
        )
      }
      .runWith(Producer.commitableSink(producerSettings))
  }

  private def prepareTopic1 = {
    Source(1 to 5)
      .map(_.toString)
      .map { x =>
        new ProducerRecord[Array[Byte], String]("topic1", x)
      }
      .runWith(Producer.plainSink(producerSettings))
  }
}
