// Motoren mit Pins 6, 9, 10 und 12 verbinden
// Bluetooth-Modul mit TX -> RX und TX -> RX verbinden
int pins[] = {6, 9, 10, 12};
const int PINS = 4;
const int VIBRATE_COUNT = 3;

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
  // activateMotor (random(4));
  // delay(1000);

  if (Serial.available())
  {
    int input = ((int)Serial.read()) - 48;
    handleSerialInput(input);
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