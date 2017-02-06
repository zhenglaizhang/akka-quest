package net.zhenglai.akka.quest.rkafka

import scala.concurrent.Await

import akka.Done
import akka.kafka.{ ProducerMessage, Subscriptions }
import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.stream.scaladsl.{ Sink, Source }
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._

object ProducerAsFlowMain extends App with Environment {

  //  simple

  complex

  private[this] def complex = {
    Consumer.committableSource(consumerSettings, Subscriptions.topics("topic1"))
      .map { msg =>
        println(s"topic1 -> topic4: $msg")
        ProducerMessage.Message(
          new ProducerRecord[Array[Byte], String](
            "topic4",
            msg.record.value
          ), msg.committableOffset)
      }
      .via(Producer.flow(producerSettings))
      .mapAsync(producerSettings.parallelism) { result =>
        result.message.passThrough.commitScaladsl()
      }
      .runWith(Sink.ignore)
  }

  private def simple = {
    val done = Source(1 to 10)
      .map { n =>
        //      val partition = math.abs(n) % 2
        val partition = 0
        ProducerMessage.Message(
          new ProducerRecord[Array[Byte], String](
            "topic3",
            partition,
            null,
            n.toString
          ),
          passThrough = n
        )
      }
      .via(Producer.flow(producerSettings))
      .map { result =>
        val record = result.message.record
        println(s"${record.topic}/${record.partition} ${result.offset}: ${record.value}: ${result.message.passThrough}")
        Done
      }
      .runWith(Sink.ignore)

    Await.result(done, 2 seconds)
  }
}
