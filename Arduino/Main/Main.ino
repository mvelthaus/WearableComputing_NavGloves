#include <SoftwareSerial.h>
// Motoren mit Pins 6, 9, 10 und 12 verbinden
// Bluetooth-Modul mit TX -> RX und TX -> RX verbinden
int pins[] = {12, 6, 9, 10};
const int PINS = 4;
const int VIBRATE_COUNT = 3;
const int VIBRATE_DELAY = 300;

const int RX_PIN = 0;
const int TX_PIN = 1;

const int BAUD_RATE = 9600;

unsigned char switchCount[] = {0, 0, 0, 0};
unsigned long switchTime[] = {0, 0, 0, 0};

void setup()
{
  Serial.begin(BAUD_RATE);
  Serial1.begin(BAUD_RATE);

  for (int i = 0; i < PINS; i++)
  {
    pinMode(pins[i], OUTPUT);
  }
}

void loop()
{
  if (Serial1.available()) {
    int inChar = Serial1.read();
    //Serial.println(inChar);
    String inString = "";
    inString += (char) inChar;

    int intInput = inString.toInt();
    handleSerialInput(intInput);
  }

  unsigned long now = millis();
  for (int i = 0; i < PINS; i++) {
    if (switchCount[i] > 0 && switchTime[i] <= now) {
      switchCount[i]--;
      switchTime[i] = now + VIBRATE_DELAY;
      if (digitalRead(pins[i]) == LOW)
        digitalWrite(pins[i], HIGH);
      else
        digitalWrite(pins[i], LOW);
    }
  }

  // if (Serial.available())
  // {
  //   Serial.println(Serial.read());
  //   int input = ((int)Serial.read()) - 48;
  //   handleSerialInput(input);
  // }
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

  digitalWrite(pins[motorId], LOW);
  switchCount[motorId] = VIBRATE_COUNT * 2;
  switchTime[motorId] = millis();

  /*int pin = pins[motorId];

    for (int i = 0; i < VIBRATE_COUNT; i++)
    {
    digitalWrite(pin, HIGH);
    delay(300);
    digitalWrite(pin, LOW);
    delay(300);
  }*/
}

boolean validInput(int input)
{
  return input >= 0 && input < PINS;
}
