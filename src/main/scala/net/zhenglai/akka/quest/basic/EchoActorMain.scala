package net.zhenglai.akka.quest.basic

import scala.concurrent.Await

import scala.concurrent.duration._
import akka.actor.{ ActorSystem, Props }
import net.zhenglai.akka.quest.basic.MagicNumberActor.{ Goodbye, Greeting }

object EchoActorMain {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("mySystem")

    // top level actor
    // supervised by the actor system's provided guardian actor
    // return ActorRef: handle to the actor instance & the only way to interact with it
    val actor = system.actorOf(Props[EchoActor], "echoActor")

    actor ! "ping"
    actor ! Greeting("Zhenglai")
    actor ! 12
    actor ! Goodbye

    // TODO: how to terminate actor system gracefully
    Thread.sleep(10 * 1000)
    system.terminate()
    Await.ready(system.whenTerminated, 10.seconds)
  }

}
