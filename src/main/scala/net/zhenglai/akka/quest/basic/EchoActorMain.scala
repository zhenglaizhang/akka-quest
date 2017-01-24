package net.zhenglai.akka.quest.basic

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ ActorSystem, PoisonPill, Props }
import net.zhenglai.akka.quest.basic.MagicNumberActor.{ Goodbye, Greeting }

object EchoActorMain {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("mySystem")

    // Actors are created by passing a Props instance into the actorOf factory method
    // which is available on ActorSystem and ActorContext.

    // top level actor
    // supervised by the actor system's provided guardian actor
    // return ActorRef: handle to the actor instance & the only way to interact with it
    // [akka://mySystem/user/echoActor]
    val actor = system.actorOf(Props[EchoActor], "echoActor")

    // The ActorRef is immutable and has a one to one relationship with the Actor it represents.
    // The ActorRef is also serializable and network-aware.

    actor ! "ping"
    actor ! Greeting("Zhenglai")
    actor ! 0

    Thread.sleep(1 * 10)
    actor ! PoisonPill

    actor ! Goodbye
    Thread.sleep(1 * 1000)
    // TODO: how to terminate actor system gracefully
    system.terminate()
    Await.ready(system.whenTerminated, 10.seconds)
  }

}
