package net.zhenglai.akka.quest.rkafka

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.kafka.{ ProducerMessage, Subscriptions }
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.producer.ProducerRecord

// producer as sink
object SimpleProducer extends App with Environment {


  val done = Source(1 to 10)
    .map(_.toString)
    .map { elem =>
      new ProducerRecord[Array[Byte], String]("topic1", elem)
    }
    .runWith(Producer.plainSink(producerSettings))

  Thread.sleep(1000)

  println("demo Producer.commitableSink")
  val done2 = Consumer
    .committableSource(consumerSettings.withGroupId("gid1"), Subscriptions.topics("topic1"))
    .map { msg =>
      println(s"topic1 -> topic2: $msg")
      ProducerMessage.Message(new ProducerRecord[Array[Byte], String](
        "topic2",
        msg.record.value
      ), msg.committableOffset)
    }
    .runWith(Producer.commitableSink(producerSettings)) // at-least-once

  Await.result(done2, Duration.Inf)
  system.terminate()
}
