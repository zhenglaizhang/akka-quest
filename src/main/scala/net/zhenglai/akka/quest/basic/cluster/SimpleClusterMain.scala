package net.zhenglai.akka.quest.basic.cluster

import akka.actor.{ Actor, ActorLogging, ActorSystem, Address, Props }
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.stream.ActorMaterializer
import net.zhenglai.akka.quest.basic.cluster.ClusterView.GetMemberNodes

// A cluster is made up from collaborating actor systems called member nodes. It is important to understand that cluster membership
// happens at the level of actor systems, not individual actors.
// It does not matter whether the member nodes reside on the same host or on different ones
//  use container technologies e.g. Docker to leverage resource managers like Mesos

// nodes can join an existing cluster and existing member nodes can leave deliberately or by failure.

object ClusterView {

  case object GetMemberNodes

  final val Name = "cluster-view"

  def props: Props = Props(new ClusterView())
}

class ClusterView extends Actor with ActorLogging {
  private var members = Set.empty[Address]

  Cluster(context.system).subscribe(self, InitialStateAsEvents, classOf[MemberEvent])


  override def receive = {
    case GetMemberNodes =>
      sender() ! members
    case MemberJoined(member) =>
      log.info("Member joined: {}", member.address)
      members += member.address

    case MemberUp(member) =>
      log.info("Member up: {}", member.address)
      members += member.address

    case MemberRemoved(member, previousStatus) =>
      log.info("Member removed: {}, previousStatus: {}", member.address, previousStatus)
      members -= member.address
  }
}

object SimpleClusterMain extends App {

  // In order to join a cluster, the joining actor system must have the same name like all the other member nodes:
  implicit val system = ActorSystem("some-system")
  implicit val mat = ActorMaterializer()

  val config = system.settings.config.getConfig("demo")
}



