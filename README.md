
# 🔥 GasGuard  
## IoT and Machine Learning-Based LPG Monitoring and Safety System - IDP 2026

GasGuard is a smart LPG monitoring and safety system designed for households and small kitchens. It combines an ESP32, gas and weight sensors, Firebase, a React Native mobile application, and a Machine Learning model to provide real-time LPG monitoring, gas-leak alerts, automatic valve control, usage analysis, and cylinder depletion prediction.

---

## 🎯 Project Objectives

- Monitor the remaining LPG level in real time.
- Detect LPG leakage using the MQ-5 sensor.
- Automatically close the gas valve during a leak.
- Display temperature, humidity, gas level, and valve status.
- Predict the remaining cylinder usage time.
- Provide mobile alerts and usage history.
- Allow customers to place refill orders.
- Support customer and supplier communication.

---

## ✨ Main Features

### 🔍 Real-Time LPG Monitoring
- Remaining LPG weight
- Gas percentage
- Temperature and humidity
- Gas leakage status
- Valve position
- Live Firebase updates

### 🚨 Gas Leak Safety System
- MQ-5 gas detection
- Local buzzer alarm
- Red warning LED
- LCD warning message
- Automatic MG995 servo valve shutoff
- Mobile gas-leak notification
- Local protection even without internet

### 📊 Usage and Cost Analysis
- Daily LPG usage
- Weekly and monthly usage trends
- Estimated monthly cost
- Cylinder history
- Estimated refill date

### 🤖 Machine Learning Prediction
- Predicts the number of LPG usage days remaining
- Uses nine engineered input features
- Trained using Multiple Linear Regression
- Optimized through 100 Gradient Descent epochs
- StandardScaler used for feature normalization

### 🚚 Refill Ordering
- Select cylinder size and quantity
- Cash or online payment selection
- Google Maps location sharing
- Customer order history
- Supplier order management
- Customer–supplier chat
- Inventory and delivery tracking

---

## 🧠 Machine Learning Model

| Metric | Value |
|---|---|
| Prediction Purpose | LPG cylinder depletion-time prediction |
| Model Type | Multiple Linear Regression |
| Input Features | 9 engineered features |
| Dataset Size | 180 records |
| Training Configuration | 100 epochs, learning rate 0.03 |
| Testing R² Score | 98.83% |
| Prediction Error | MAE: 1.618 days, RMSE: 1.877 days |

### Input Features

1. `gas_weight_kg`
2. `gas_percentage`
3. `daily_usage_kg`
4. `day_of_week`
5. `weekend`
6. `consumption_rate`
7. `usage_velocity`
8. `rolling_avg_7`
9. `days_since_refill`

### Final Prediction Equation

```text
Days_Remaining =
27.3480
+ 5.5789(gas_weight)
+ 0.2789(gas_percentage)
- 20.2193(daily_usage)
+ 0.2142(day_of_week)
+ 0.5944(weekend)
- 96.5310(consumption_rate)
- 60.2001(usage_velocity)
- 277.5059(rolling_avg_7)
- 0.0240(days_since_refill)
