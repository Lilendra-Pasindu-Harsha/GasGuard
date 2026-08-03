"""
PC Real-Time Keyboard Controller for ESP32 MG945 Servo
Captures Arrow Keys and displays real-time microsecond telemetry from ESP32.
"""
import serial
import time
import tkinter as tk
from tkinter import messagebox

# ==========================================
# CONFIGURATION: Set your ESP32 Serial Port
# Windows Example: 'COM3', 'COM4'
# Linux/Mac Example: '/dev/ttyUSB0', '/dev/tty.SLAB_USBtoUART'
# ==========================================
SERIAL_PORT = 'COM3'
BAUD_RATE = 115200

# Initialize Serial Connection
try:
    esp = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=0.1)
    time.sleep(2)  # Allow ESP32 auto-reset to complete after serial connection
    print(f"Successfully connected to ESP32 on {SERIAL_PORT}")
except Exception as e:
    print(f"Failed to connect to {SERIAL_PORT}: {e}")
    exit()

def send_command(cmd_char):
    """Send a single ASCII character instantly to the ESP32."""
    if esp and esp.is_open:
        esp.write(cmd_char.encode('utf-8'))

def check_serial_telemetry():
    """Non-blocking read of serial buffer to update the UI with ESP32 feedback."""
    while esp.in_waiting:
        try:
            line = esp.readline().decode('utf-8', errors='ignore').strip()
            if "Target Pulse Width:" in line:
                # Extract just the value and unit for clean display
                val_str = line.split(":")[-1].strip()
                position_var.set(val_str)
                print(f"Telemetry: {val_str}")
        except Exception as err:
            print(f"Serial read error: {err}")
    
    # Schedule this function to run again in 20 milliseconds (50 fps UI refresh)
    root.after(20, check_serial_telemetry)

def on_close():
    """Cleanly close serial port when exiting."""
    if esp and esp.is_open:
        esp.close()
    root.destroy()

# ==========================================
# Build GUI Window
# ==========================================
root = tk.Tk()
root.title("MG945 Keyboard Calibrator")
root.geometry("400x260")
root.resizable(False, False)
root.protocol("WM_DELETE_WINDOW", on_close)

# Header
tk.Label(root, text="MG945 Real-Time Calibrator", font=("Arial", 14, "bold")).pack(pady=(15, 5))
tk.Label(root, text=f"Connected to {SERIAL_PORT} @ {BAUD_RATE} baud", font=("Arial", 9, "italic"), fg="green").pack(pady=(0, 15))

# Real-time Telemetry Display
position_var = tk.StringVar(value="1500 us")
telemetry_frame = tk.Frame(root, bd=2, relief="groove", padx=20, pady=10)
telemetry_frame.pack(pady=5)
tk.Label(telemetry_frame, text="Current Pulse Width:", font=("Arial", 10)).pack()
tk.Label(telemetry_frame, textvariable=position_var, font=("Consolas", 20, "bold"), fg="#0055ff").pack()

# Controls Instruction
controls_text = (
    "KEYBOARD CONTROLS (Keep window focused):\n"
    "• Left / Right Arrows : Fine Step (±10 us)\n"
    "• Up / Down Arrows    : Coarse Step (±50 us)\n"
    "• R Key               : Reset to Center (1500 us)"
)
tk.Label(root, text=controls_text, font=("Consolas", 9), justify="left").pack(pady=15)

# Bind Keyboard Events
root.bind('<Right>', lambda e: send_command('+'))
root.bind('<Left>',  lambda e: send_command('-'))
root.bind('<Up>',    lambda e: send_command('d'))
root.bind('<Down>',  lambda e: send_command('a'))
root.bind('<r>',     lambda e: send_command('r'))
root.bind('<R>',     lambda e: send_command('r'))

# Start non-blocking serial telemetry listener
root.after(20, check_serial_telemetry)

# Launch Application
print("GUI running. Press Arrow keys inside the window to steer.")
root.mainloop()