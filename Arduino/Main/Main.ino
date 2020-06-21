#include <SoftwareSerial.h>
// Motoren mit Pins 6, 9, 10 und 12 verbinden
// Bluetooth-Modul mit TX -> RX und TX -> RX verbinden
int pins[] = {6, 9, 10, 12};
const int PINS = 4;
const int VIBRATE_COUNT = 3;

const int RX_PIN = 0;
const int TX_PIN = 1;

//SoftwareSerial blueSerial(RX_PIN, TX_PIN);
const int BAUD_RATE = 9600;

void setup()
{
  Serial.begin(BAUD_RATE);
  Serial1.begin(BAUD_RATE);
  //blueSerial.begin(BAUD_RATE);

  for (int i = 0; i < PINS; i++)
  {
    pinMode(pins[i], OUTPUT);
  }
}

void loop()
{
  // Simulation
  // activateMotor (random(4));
  // delay(1000);

  if (Serial1.available()) {
    int inChar = Serial1.read();
    //Serial.println(inChar);
    String inString = ""; 
    inString += (char) inChar;

    int intInput = inString.toInt();
    // Serial.print("Input=");
    // Serial.println(intInput);
    handleSerialInput(intInput);
  }

  if (Serial.available())
  {
    int inChar = Serial.read();
    //Serial.println(inChar);
    String inString = ""; 
    inString += (char) inChar;

    int intInput = inString.toInt();
    // Serial.print("Input=");
    // Serial.println(intInput);
    Serial.println(Serial.read());
    handleSerialInput(intInput);
  }
}

void handleSerialInput(int input)
{
  if (validInput(input))
  {
    activateMotor(input);
  }
  else
  {
    Serial.print("Invalid input=");
    Serial.println(input);
  }
}

void activateMotor(int motorId)
{
  Serial.print("Activate Motor: ");
  Serial.println(motorId);

  Serial1.print("Activate Motor: ");
  Serial1.println(motorId);

  int pin = pins[motorId];

  for (int i = 0; i < VIBRATE_COUNT; i++)
  {
    digitalWrite(pin, HIGH);
    delay(300);
    digitalWrite(pin, LOW);
    delay(300);
  }
}

boolean validInput(int input)
{
  for (int i = 0; i < PINS; i++)
  {
    if (input == i)
    {
      return true;
    }
  }
  return false;
}