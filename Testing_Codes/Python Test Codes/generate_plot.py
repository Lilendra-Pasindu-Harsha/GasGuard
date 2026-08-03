import numpy as np
import matplotlib.pyplot as plt

# User data points
x_data = np.array([0, 2.010, 2.700, 3.000])  # Weight in kg
y_data = np.array([397352.0, 572627.0, 652793.0, 673847.0])  # Raw ADC values

# Perform linear regression
slope, intercept = np.polyfit(x_data, y_data, 1)

# Calculate R-squared value
residuals = y_data - (slope * x_data + intercept)
ss_res = np.sum(residuals**2)
ss_tot = np.sum((y_data - np.mean(y_data))**2)
r_squared = 1 - (ss_res / ss_tot)

# Define range for fitting line
x_line = np.linspace(-0.2, 3.5, 100)
y_line = slope * x_line + intercept

# Plot styling
plt.style.use('seaborn-v0_8-whitegrid')
fig, ax = plt.subplots(figsize=(9, 6.5), dpi=150)

# Plot fitted line
ax.plot(x_line, y_line, color='#2563EB', linestyle='-', linewidth=2.5, 
        label=f'Fitted Line: y = {slope:.2f}*x + {intercept:.2f}')

# Plot measured data points
ax.scatter(x_data, y_data, color='#EF4444', s=100, zorder=5, 
           label='Measured Data Points')

# Set specific horizontal/vertical alignments and offsets for each point
# to guarantee no clipping or line crossing.
alignments = [
    ('left', 'bottom', 12, 10),    # Point 0: Top-Right (prevents left clipping)
    ('left', 'top', 12, -10),      # Point 1: Bottom-Right
    ('right', 'bottom', -12, 10),  # Point 2: Top-Left (aligned close to point)
    ('left', 'top', 12, -10)       # Point 3: Bottom-Right
]

labels = [
    'Platform Only\n(0.0 kg, 397352)', 
    'Test Weight\n(2.01 kg, 572627)', 
    'Test Weight\n(2.70 kg, 652793)', 
    'Test Weight\n(3.00 kg, 673847)'
]

for i in range(len(x_data)):
    ha, va, x_off, y_off = alignments[i]
    ax.annotate(labels[i], (x_data[i], y_data[i]), 
                xytext=(x_off, y_off), textcoords='offset points',
                ha=ha, va=va, fontsize=9.5, fontweight='bold', color='#1F2937',
                bbox=dict(boxstyle='round,pad=0.3', facecolor='#F9FAFB', edgecolor='#D1D5DB', alpha=0.95))

# Titles and labels
ax.set_title('Load Cell Calibration: Raw ADC vs. Added Weight', fontsize=14, fontweight='bold', pad=15)
ax.set_xlabel('Added Weight (kg)', fontsize=11, labelpad=8)
ax.set_ylabel('Raw ADC Count', fontsize=11, labelpad=8)

# Legend and text details
ax.legend(loc='upper left', frameon=True, facecolor='white', edgecolor='#E5E7EB')
info_text = f"Calibration Factor: {slope:.2f} counts/kg\nTare Offset: {intercept:.2f}\nR² Quality: {r_squared:.4f}"
ax.text(0.05, 0.65, info_text, transform=ax.transAxes, fontsize=10, 
        bbox=dict(boxstyle='round,pad=0.5', facecolor='#F3F4F6', edgecolor='#E5E7EB'))

# Layout adjustments
plt.xlim(-0.3, 3.6)
plt.ylim(350000, 720000)
plt.tight_layout()

# Save image directly to artifacts folder
output_path = r"C:\Users\thusa\.gemini\antigravity-ide\brain\aa75ff76-f0c8-4795-a7a2-3c8e67702710\calibration_graph.png"
plt.savefig(output_path, dpi=300)
print(f"[SUCCESS] Calibration graph updated and saved to: {output_path}")
