package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorSystem, Kill, Props }

class SupervisionActor extends Actor with ActorLogging {
  override def receive = {
    case msg => log.info("received: {}", msg)
  }
}

object SupervisionActorMain extends App {

  val system = ActorSystem("supervisionActorSystem")
  val actor = system.actorOf(Props[SupervisionActor], name = "supervisionActor")
  actor ! "wow"

  // cause the actor to throw a ActorKilledException, triggering a failure.
  // The actor will suspend operation and its supervisor will be asked how to handle the failure, which may mean resuming the actor, restarting it or terminating it completely.
  actor ! Kill

  Thread.sleep(1000L)
  system.terminate()
}
