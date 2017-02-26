package com.doanduyhai.elevator.actors

import java.io.{ByteArrayOutputStream, PrintStream}

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.doanduyhai.elevator.ExpectedOutputParser
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable.Queue

class SimulationActorTest extends TestKit(ActorSystem("SimulationActorSystem",
  ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with ExpectedOutputParser {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "SimulationActor" must {

    "trigger simulation" in {
      //Given
      val simulationActor = system.actorOf(Props(new SimulationActor(testActor, testActor)))

      //When
      simulationActor ! StartSimulation

      //Then
      expectMsg(StartSimulation)
    }

    "print elevators state when receiving ElevatorStatusReply" in {
      //Given
      val controlSystem = TestProbe()
      val baos = new ByteArrayOutputStream()
      val simulationActor = system.actorOf(Props(new SimulationActor(testActor, controlSystem.ref,
        printStream = new PrintStream(baos))))

      //When
      simulationActor ! ElevatorsStatuses(Map(
        1 -> (AtFloor(5), None),
        2 -> (Move(1,4), None),
        3 -> (AtFloor(3), Some(Pickup(Move(4,0)))),
        4 -> (Move(5,2), Some(Pickup(Move(1,8))))))

      //Then
      expectMsg(ElevatorsStatusesAck)
      val display = new String(baos.toByteArray())

      display shouldBe readContentFromFile("SimulationActorTest/print_elevators_state_when_receiving_ElevatorStatusReply")
    }

    "process Pickup order" in {
      //Given
      val controlSystem = TestProbe()
      val root = TestProbe()
      val baos = new ByteArrayOutputStream()
      val pickup = Pickup(Move(3,0))
      val simulationActor = system.actorOf(Props(new SimulationActor(root.ref, controlSystem.ref,
        printStream = new PrintStream(baos))))
      simulationActor ! ElevatorsStatuses(Map(1 -> (AtFloor(5), None)))
      expectMsg(ElevatorsStatusesAck)

      //When
      simulationActor ! pickup

      //Then
      controlSystem.expectMsg(pickup)
      val display = new String(baos.toByteArray())
      display shouldBe readContentFromFile("SimulationActorTest/process_Pickup_order")
    }

    "get elevator status periodically" in {
      //Given
      val controlSystem = TestProbe()
      val simulationActor = system.actorOf(Props(new SimulationActor(testActor, controlSystem.ref)))

      //When
      simulationActor ! ElevatorsStatuses(Map(1 -> (Move(2,0), None)))
      expectMsg(ElevatorsStatusesAck)
      simulationActor ! RunSimulation

      //Then
      simulationActor ! ElevatorsStatuses(Map(1 -> (AtFloor(0), None)))
      expectMsgAllOf(ElevatorsStatusesAck, SimulationFinished)
    }

    "print order queue" in {
      //Given
      val controlSystem = TestProbe()
      val baos = new ByteArrayOutputStream()
      val simulationActor = system.actorOf(Props(new SimulationActor(testActor, controlSystem.ref,
        printStream = new PrintStream(baos))))

      //When
      simulationActor ! OrderQueue(Queue(Pickup(Move(0,3)), Pickup(Move(5,2))))

      //Then
      expectMsg(OrderQueueAck)
      val display = new String(baos.toByteArray())
      display should include("Control system orders queue: [Move(0,3),Move(5,2)]")
    }
  }
}
