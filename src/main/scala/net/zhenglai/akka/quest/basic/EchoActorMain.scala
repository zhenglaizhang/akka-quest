package net.zhenglai.akka.quest.basic

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.typed.Inbox
import net.zhenglai.akka.quest.basic.MagicNumberActor.{ Goodbye, Greeting }

object EchoActorMain {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("mySystem")

    // Actors are created by passing a Props instance into the actorOf factory method
    // which is available on ActorSystem and ActorContext.

    // top level actor
    // supervised by the actor system's provided guardian actor
    // return ActorRef: handle to the actor instance & the only way to interact with it
    // Actors are automatically started asynchronously when created.
    // [akka://mySystem/user/echoActor]
    val actor = system.actorOf(Props[EchoActor], "echoActor")

    // The ActorRef is immutable and has a one to one relationship with the Actor it represents.
    // The ActorRef is also serializable and network-aware.

    actor ! "unknown"
    actor ! "ping"
    actor ! Greeting("Zhenglai")
    actor ! 0


    // When writing code outside of actors which shall communicate with actors,
    // the ask pattern can be a solution

    val magicActorOut = system.actorOf(MagicNumberActor.props(9999), "magicActorOut")
    magicActorOut ! "unhandled"
    // There is an implicit conversion from inbox to actor reference which means that in this example the sender reference will be that of the actor hidden away within the inbox

    // TODO: http://doc.akka.io/docs/akka/2.4/scala/actors.html#Forward_message
//    implicit val inbox = new Inbox[Int]("inbox")
//    magicActorOut ! 1
//    Thread.sleep(1 * 1000)
//    require(inbox.receiveMsg() == 10000)
    actor ! PoisonPill

    actor ! Goodbye
    Thread.sleep(1 * 1000)
    // TODO: how to terminate actor system gracefully
    system.terminate()
    Await.ready(system.whenTerminated, 10.seconds)
  }
}
