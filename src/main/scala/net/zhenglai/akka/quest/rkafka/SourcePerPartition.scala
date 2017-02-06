package net.zhenglai.akka.quest.rkafka

import akka.actor.ActorRef
import akka.kafka.ConsumerMessage.{ CommittableMessage, CommittableOffsetBatch }
import akka.kafka.{ KafkaConsumerActor, Subscriptions }
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{ Keep, Sink }
import org.apache.kafka.common.TopicPartition

object SourcePerPartition extends App with Environment {
  //  sourcePerPartition

  //  consumerGroup

  //  complex()


  private[this] def sharingConsumer() = {
    // If you have many streams it can be more efficient to share
    // the underlying KafkaConsumer. That can be shared via the
    // KafkaConsumerActor. You need to create the actor and stop
    // it when it is not needed any longer. You pass the ActorRef
    // as a parameter to the Consumer factory methods.

    // consumer is represented by actor
    val consumer: ActorRef = system.actorOf(
      KafkaConsumerActor.props(consumerSettings.withGroupId("gid16"))
    )

    // manually assign topic partition to it
    Consumer
      .plainExternalSource[Array[Byte], String](consumer, Subscriptions.assignment(new TopicPartition("topic1", 0)))
      .map { msg => println(msg); msg }
      .runWith(Sink.ignore)


    // manually assign another topic partition
    Consumer
      .plainExternalSource[Array[Byte], String](consumer, Subscriptions.assignment(new TopicPartition("topic1", 1))) // another partition
      .map { msg => println(msg); msg }
      .runWith(Sink.ignore)
  }

  private[this] def complex() = {
    type Msg = CommittableMessage[Array[Byte], String]

    //  def zipper(left: Source[Msg, _], right: Source[Msg, _]): Source[(Msg, Msg), NotUsed] = ???

    // TODO: fix it
    val maxPartitions = 1
    Consumer.committablePartitionedSource(consumerSettings.withGroupId("gid15"), Subscriptions.topics("topic1"))
      .map {
        case (topicPartition, source) =>
          val otherSource = {
            val otherTopicPartition = new TopicPartition("topic2", topicPartition.partition())
            Consumer
              .committableSource(consumerSettings, Subscriptions.assignment(otherTopicPartition))
          }
          source zip otherSource
      }
      .flatMapMerge(maxPartitions, identity)
      //.via(business)
      .map { msg => println(msg); msg }
      .batch(max = 20, {
        case (l, r) => (
          CommittableOffsetBatch.empty.updated(l.committableOffset),
          CommittableOffsetBatch.empty.updated(r.committableOffset)
        )
      }) { case ((batchL, batchR), (l, r)) =>
        batchL.updated(l.committableOffset)
        batchR.updated(r.committableOffset)
        (batchL, batchR)
      }
      .mapAsync(1) { case (l, r) => l.commitScaladsl().map(_ => r) }
      .mapAsync(1)(_.commitScaladsl())
      .runWith(Sink.ignore)
  }

  private def consumerGroup = {
    val maxPartitions = 1
    //Consumer group represented as Source[(TopicPartition, Source[Messages])]
    val consumerGroup = Consumer
      .committablePartitionedSource(
        consumerSettings.withGroupId("gid13"),
        Subscriptions.topics("topic1")
      )
      .map { case (topicPartition, source) =>
        // process each assigned partition separately
        source
          // .via(business)
          .map { msg => println(msg); msg }
          .toMat(Sink.ignore)(Keep.both)
          .run()
      }
      .mapAsyncUnordered(maxPartitions)(_._2)
      .runWith(Sink.ignore)
  }

  private def sourcePerPartition = {
    val maxPartitions = 1
    val done = Consumer.committablePartitionedSource(
      consumerSettings.withGroupId("gid12"),
      Subscriptions.topics("topic1")
    )
      // Consumer.plainPartitionedSource and Consumer.committablePartitionedSource supports
      // tracking the automatic partition assignment from Kafka.
      // When topic-partition is assigned to a consumer this source will
      // emit tuple with assigned topic-partition and a corresponding source.
      // When topic-partition is revoked then corresponding source completes.
      .flatMapMerge(maxPartitions, _._2)
      .map { msg =>
        println(s"consuming: $msg")
        msg
      }
      .batch(// Backpressure per partition with batch commit:
        max = 20,
        seed = first => CommittableOffsetBatch.empty.updated(first.committableOffset)
      ) { (batch, elem) => batch.updated(elem.committableOffset) }
      .mapAsync(3)(_.commitScaladsl())
      .runWith(Sink.ignore)
  }
}
