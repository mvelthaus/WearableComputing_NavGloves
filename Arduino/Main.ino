int pins[] = {6, 9, 10, 12};
int VIBRATE_COUNT = 3;

void setup()
{
  Serial.begin(9600);

  for (int i = 0; i < 4; i++)
  {
    pinMode(pins[i], OUTPUT);
  }
}

void loop()
{
  // Simulation
  activateMotor (random(4));
  delay(1000);
}

void activateMotor(int motorId)
{
  Serial.print("Activate Motor: ");
  Serial.println(motorId);

  int pin = pins[motorId];

  for (int i = 0; i < VIBRATE_COUNT; i++)
  {
    digitalWrite(pin, HIGH);
    delay(300);
    digitalWrite(pin, LOW);
    delay(300);
  }
}