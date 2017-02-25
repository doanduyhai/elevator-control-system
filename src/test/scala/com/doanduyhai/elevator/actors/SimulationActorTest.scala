package com.doanduyhai.elevator.actors

import java.io.{ByteArrayOutputStream, PrintStream}

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class SimulationActorTest extends TestKit(ActorSystem("SimulationActorSystem",
  ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "SimulationActor" must {

    "trigger simulation" in {
      //Given
      val simulationActor = system.actorOf(Props(new SimulationActor(testActor)))

      //When
      simulationActor ! ExecuteSimulation

      //Then
      expectMsg(ExecuteSimulation)
    }

    "print elevators state when receiving ElevatorStatusReply" in {
      //Given
      val controlSystem = TestProbe()
      val baos = new ByteArrayOutputStream()
      val simulationActor = system.actorOf(Props(new SimulationActor(controlSystem.ref,
        printStream = new PrintStream(baos))))

      //When
      simulationActor ! ElevatorStatusReply(Map(
        1 -> (AtFloor(5), None),
        2 -> (Move(1,4), None),
        3 -> (AtFloor(3), Some(Pickup(Move(4,0)))),
        4 -> (Move(5,2), Some(Pickup(Move(1,8))))))

      //Then
      expectMsg("OK")
      val display = new String(baos.toByteArray())

      display shouldBe
        "1[    ]:  _  _  _  _  _ |5|\n" +
        "2[    ]:  _ |1> _  _  _ {4}\n" +
        "3[4->0]:  _  _  _ |3|\n" +
        "4[1->8]:  _  _ {2} _  _  _ <5|\n"
    }

    "process Pickup order" in {
      //Given
      val controlSystem = TestProbe()
      val baos = new ByteArrayOutputStream()
      val pickup = Pickup(Move(3,0))
      val simulationActor = system.actorOf(Props(new SimulationActor(controlSystem.ref,
        printStream = new PrintStream(baos))))
      simulationActor ! ElevatorStatusReply(Map(1 -> (AtFloor(5), None)))
      expectMsg("OK")

      //When
      simulationActor ! pickup

      //Then
      controlSystem.expectMsg(pickup)
      val display = new String(baos.toByteArray())

      display shouldBe
        "1[    ]:  _  _  _  _  _ |5|\n" +
        s"\n\t\tOrder received: $pickup \n\n"
    }

    "get elevator status periodically" in {
      //Given
      val controlSystem = TestProbe()
      val simulationActor = system.actorOf(Props(new SimulationActor(controlSystem.ref)))

      //When
      simulationActor ! GetElevatorStatus

      //Then
      controlSystem.expectMsg(GetElevatorStatus)
      simulationActor ! ElevatorStatusReply(Map(1 -> (AtFloor(0), None)))
      expectMsg("OK")
    }
  }
}
