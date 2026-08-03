# ================================================================
# GASGUARD - LPG DEPLETION PREDICTION
# MULTIPLE LINEAR REGRESSION TRAINING (CMD VERSION)
# ================================================================

import os
import time
import json
import joblib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from sklearn.model_selection import (
    train_test_split,
    KFold,
    cross_val_score
)

from sklearn.preprocessing import (
    StandardScaler,
    PolynomialFeatures
)

from sklearn.linear_model import (
    LinearRegression,
    Ridge,
    Lasso
)

from sklearn.pipeline import Pipeline

from sklearn.metrics import (
    r2_score,
    mean_absolute_error,
    mean_squared_error
)

# ================================================================
# CONFIGURATION
# ================================================================

DATASET = "train_dataset.csv"

FEATURES = [
    "gas_weight_kg",
    "gas_percentage",
    "daily_usage_kg",
    "day_of_week",
    "weekend",
    "consumption_rate",
    "rolling_avg_7"
]

TARGET = "days_remaining"

EPOCHS = 100
LEARNING_RATE = 0.05
RANDOM_STATE = 42

# Create directories
os.makedirs('gasguard_models', exist_ok=True)
os.makedirs('gasguard_plots', exist_ok=True)

# ================================================================
# LOAD DATASET
# ================================================================

print("=" * 75)
print("        GASGUARD - FEATURE ENGINEERING & PREPROCESSING")
print("=" * 75)

if not os.path.exists(DATASET):
    raise FileNotFoundError(
        f"❌ '{DATASET}' not found! Place it in the same folder."
    )

print(f"\n📂 Loading dataset: {DATASET}")
df = pd.read_csv(DATASET)

if "date" in df.columns:
    df["date"] = pd.to_datetime(
        df["date"],
        errors="coerce"
    )

print(f"✓ Dataset loaded successfully")
print(f"✓ Total Records : {len(df)}")
print(f"✓ Total Columns : {len(df.columns)}")

# ------------------------------------------------------------
# DISPLAY DATASET COLUMNS
# ------------------------------------------------------------

print("\n📋 DATASET COLUMNS")
print("-" * 75)

for i, column in enumerate(df.columns, 1):
    print(f"{i:02d}. {column}")

# ================================================================
# CREATE MISSING FEATURES
# ================================================================

if "gas_percentage" not in df.columns:
    df["gas_percentage"] = (
        df["gas_weight_kg"] / 5.0
    ) * 100

if "day_of_week" not in df.columns:
    if "date" not in df.columns:
        raise ValueError(
            "day_of_week missing and no date column available."
        )
    df["day_of_week"] = (
        df["date"].dt.dayofweek
    )

if "weekend" not in df.columns:
    df["weekend"] = (
        df["day_of_week"] >= 5
    ).astype(int)

if "consumption_rate" not in df.columns:
    df["consumption_rate"] = np.where(
        df["gas_weight_kg"] > 0,
        df["daily_usage_kg"]
        / df["gas_weight_kg"],
        0
    )

if "rolling_avg_7" not in df.columns:
    df["rolling_avg_7"] = (
        df["daily_usage_kg"]
        .rolling(
            window=7,
            min_periods=1
        )
        .mean()
    )

# Only calculate target if your CSV does not already contain it
if TARGET not in df.columns:
    avg_usage = (
        df["rolling_avg_7"]
        .replace(0, np.nan)
    )
    df[TARGET] = (
        df["gas_weight_kg"]
        / avg_usage
    )

# Remove invalid values
df = df.replace(
    [np.inf, -np.inf],
    np.nan
)

df = df.dropna(
    subset=FEATURES + [TARGET]
).reset_index(drop=True)

# ------------------------------------------------------------
# DISPLAY FINAL MODEL CONFIGURATION
# ------------------------------------------------------------

print("\n" + "=" * 75)
print("               FINAL MODEL CONFIGURATION")
print("=" * 75)

print("\n✓ 7 Predictive Features:")
for i, feature in enumerate(
    FEATURES,
    1
):
    print(f"  {i}. {feature}")

print(f"\n✓ Target Variable : {TARGET}")
print(f"✓ Feature Matrix  : {df[FEATURES].shape}")
print(f"✓ Target Vector   : {df[TARGET].shape}")

# ------------------------------------------------------------
# DISPLAY SAMPLE DATA
# ------------------------------------------------------------

print("\n📊 FIRST 10 MODEL RECORDS")
print("-" * 75)
print(
    df[FEATURES + [TARGET]]
    .head(10)
    .to_string(index=False)
)

print("\n✓ Feature engineering completed successfully.")

X = df[FEATURES].astype(float)
y = df[TARGET].astype(float)

# ================================================================
# TRAIN / TEST SPLIT
# ================================================================

X_train_full, X_test, y_train_full, y_test = train_test_split(
    X,
    y,
    test_size=0.20,
    random_state=RANDOM_STATE,
    shuffle=True
)

print("\n" + "=" * 75)
print(" DATA SPLIT CONFIGURATION")
print("=" * 75)

print(
    f"Training Set : {len(X_train_full)} "
    f"samples "
    f"({len(X_train_full)/len(X)*100:.1f}%)"
)

print(
    f"Testing Set  : {len(X_test)} "
    f"samples "
    f"({len(X_test)/len(X)*100:.1f}%)"
)
print(f"Random State : {RANDOM_STATE}")

# ================================================================
# INTERNAL TRAINING / VALIDATION SPLIT
# Used for real epoch convergence analysis
# ================================================================

X_train, X_val, y_train, y_val = train_test_split(
    X_train_full,
    y_train_full,
    test_size=0.20,
    random_state=RANDOM_STATE,
    shuffle=True
)

scaler_epoch = StandardScaler()
X_train_scaled = scaler_epoch.fit_transform(X_train)
X_val_scaled = scaler_epoch.transform(X_val)

# Add intercept
X_train_b = np.c_[
    np.ones((X_train_scaled.shape[0], 1)),
    X_train_scaled
]

X_val_b = np.c_[
    np.ones((X_val_scaled.shape[0], 1)),
    X_val_scaled
]

# ================================================================
# REAL GRADIENT DESCENT TRAINING
# ================================================================

theta = np.zeros(X_train_b.shape[1])
m = len(y_train)

train_losses = []
validation_losses = []
validation_scores = []
gradient_norms = []

print("\n" + "=" * 75)
print(" MODEL TRAINING - MULTIPLE LINEAR REGRESSION")
print("=" * 75)

print(f"\nEpochs        : {EPOCHS}")
print(f"Learning Rate : {LEARNING_RATE}")
print("Optimizer     : Batch Gradient Descent")
print("Loss Function : Mean Squared Error (MSE)")

start_time = time.time()

for epoch in range(1, EPOCHS + 1):
    # FORWARD PASS
    prediction = X_train_b @ theta
    error = prediction - y_train.to_numpy()

    # GRADIENT
    gradient = (2 / m) * (X_train_b.T @ error)

    # UPDATE MODEL WEIGHTS
    theta = theta - LEARNING_RATE * gradient

    # CURRENT MODEL PERFORMANCE
    train_prediction = X_train_b @ theta
    validation_prediction = X_val_b @ theta

    train_mse = mean_squared_error(y_train, train_prediction)
    validation_mse = mean_squared_error(y_val, validation_prediction)
    validation_r2 = r2_score(y_val, validation_prediction)
    gradient_norm = np.linalg.norm(gradient)

    train_losses.append(train_mse)
    validation_losses.append(validation_mse)
    validation_scores.append(validation_r2)
    gradient_norms.append(gradient_norm)

    # REPORT SCREENSHOT LOGS
    if epoch in [1, 25, 50, 75, 100]:
        print()
        print(f"[Epoch {epoch:03d}/{EPOCHS}] Training Linear Regression model...")
        
        if epoch == 1:
            print("  Forward pass completed")
            print("  Computing gradients...")
            print(f"  Updating weights with learning rate: {LEARNING_RATE}")
        elif epoch == 25:
            print("  Continuing training...")
        elif epoch == 50:
            print("  Optimizing regression weights...")
            print("  Feature coefficient refinement...")
        elif epoch == 75:
            print("  Model convergence in progress...")
        elif epoch == 100:
            print("  Final training pass...")

        print(f"  Current Loss (MSE) : {train_mse:.4f}")
        print(f"  Validation Loss    : {validation_mse:.4f}")
        print(f"  Validation Score   : {validation_r2:.4f}")
        print(f"  Gradient Norm      : {gradient_norm:.6f}")

training_time = time.time() - start_time
print()
print(f"100 epochs completed in {training_time:.4f} seconds.")

# ================================================================
# FINAL MODEL TRAINING
# Train again using the whole 80% training dataset
# ================================================================

scaler = StandardScaler()
X_train_final = scaler.fit_transform(X_train_full)
X_test_final = scaler.transform(X_test)

X_train_final_b = np.c_[
    np.ones((X_train_final.shape[0], 1)),
    X_train_final
]

X_test_final_b = np.c_[
    np.ones((X_test_final.shape[0], 1)),
    X_test_final
]

theta_final = np.zeros(X_train_final_b.shape[1])

for epoch in range(EPOCHS):
    prediction = X_train_final_b @ theta_final
    error = prediction - y_train_full.to_numpy()
    gradient = (2 / len(y_train_full)) * (X_train_final_b.T @ error)
    theta_final -= LEARNING_RATE * gradient

# ================================================================
# FINAL PREDICTIONS
# ================================================================

y_train_pred = X_train_final_b @ theta_final
y_test_pred = X_test_final_b @ theta_final

# ================================================================
# PERFORMANCE METRICS
# ================================================================

train_r2 = r2_score(y_train_full, y_train_pred)
test_r2 = r2_score(y_test, y_test_pred)
mae = mean_absolute_error(y_test, y_test_pred)
mse = mean_squared_error(y_test, y_test_pred)
rmse = np.sqrt(mse)

# Ignore zero targets for MAPE
y_test_array = y_test.to_numpy()
mask = np.abs(y_test_array) > 1e-12
mape = np.mean(np.abs((y_test_array[mask] - y_test_pred[mask]) / y_test_array[mask])) * 100

# ================================================================
# MODEL EVALUATION OUTPUT
# ================================================================

print("\n" + "=" * 78)
print(" MODEL EVALUATION & METRICS")
print("=" * 78)

print("\nPerformance Metrics:\n")
print(f"R² Score (Training)          : {train_r2*100:.2f}%")
print(f"R² Score (Testing)           : {test_r2*100:.2f}%")
print(f"Mean Absolute Error (MAE)    : {mae:.3f} days")
print(f"MAE in Hours                 : {mae*24:.2f} hours")
print(f"Mean Squared Error (MSE)     : {mse:.3f} days²")
print(f"Root Mean Square Error       : {rmse:.3f} days")
print(f"Mean Absolute Percentage Err.: {mape:.2f}%")

# ================================================================
# 5-FOLD CROSS VALIDATION
# ================================================================

cv_pipeline = Pipeline([
    ("Scaler", StandardScaler()),
    ("LinearRegression", LinearRegression())
])

kf = KFold(n_splits=5, shuffle=True, random_state=RANDOM_STATE)
cv_scores = cross_val_score(cv_pipeline, X, y, cv=kf, scoring="r2")

print("\nCross-Validation Results (5-Fold)")
print("-" * 78)
for i, score in enumerate(cv_scores, start=1):
    print(f"Fold {i}: {score*100:.2f}%")

print(f"\nMean CV Score: {cv_scores.mean()*100:.2f}% ± {cv_scores.std()*100:.2f}%")

# ================================================================
# FINAL COEFFICIENTS
# Convert standardized coefficients to original units
# ================================================================

scaled_coefficients = theta_final[1:]
original_coefficients = scaled_coefficients / scaler.scale_
original_intercept = theta_final[0] - np.sum(scaled_coefficients * scaler.mean_ / scaler.scale_)

print("\n" + "=" * 78)
print(" MODEL COEFFICIENTS - FEATURE IMPORTANCE")
print("=" * 78)
print(f"\nIntercept: {original_intercept:+.6f}")

for feature, coefficient in zip(FEATURES, original_coefficients):
    effect = "POSITIVE" if coefficient >= 0 else "NEGATIVE"
    print(f"{feature:22s} : {coefficient:+.6f} ({effect})")

# ================================================================
# FINAL EQUATION
# ================================================================

equation = f"Days_Remaining = {original_intercept:.6f}"
for feature, coefficient in zip(FEATURES, original_coefficients):
    if coefficient >= 0:
        equation += f" + {coefficient:.6f}({feature})"
    else:
        equation += f" - {abs(coefficient):.6f}({feature})"

print("\n" + "=" * 78)
print(" FINAL MODEL EQUATION")
print("=" * 78)
print("\n" + equation)

# ================================================================
# MODEL COMPARISON
# ================================================================

models = {
    "Linear Regression": Pipeline([
        ("Scaler", StandardScaler()),
        ("Model", LinearRegression())
    ]),
    "Ridge (L2)": Pipeline([
        ("Scaler", StandardScaler()),
        ("Model", Ridge(alpha=1.0))
    ]),
    "Lasso (L1)": Pipeline([
        ("Scaler", StandardScaler()),
        ("Model", Lasso(alpha=0.01, max_iter=10000))
    ]),
    "Polynomial (deg=2)": Pipeline([
        ("Scaler", StandardScaler()),
        ("Polynomial", PolynomialFeatures(degree=2, include_bias=False)),
        ("Model", LinearRegression())
    ])
}

comparison_results = []
for name, model in models.items():
    model.fit(X_train_full, y_train_full)
    prediction = model.predict(X_test)
    r2 = r2_score(y_test, prediction) * 100
    model_mae = mean_absolute_error(y_test, prediction)
    model_rmse = np.sqrt(mean_squared_error(y_test, prediction))
    comparison_results.append({
        "Model": name,
        "R² Score (%)": r2,
        "MAE (days)": model_mae,
        "RMSE (days)": model_rmse
    })

comparison_df = pd.DataFrame(comparison_results)
linear_score = comparison_df.loc[
    comparison_df["Model"] == "Linear Regression",
    "R² Score (%)"
].iloc[0]

comparison_df["Improvement vs Baseline"] = (
    comparison_df["R² Score (%)"] - linear_score
)

# ================================================================
# RECOMMENDATION
# ================================================================

best_score = comparison_df["R² Score (%)"].max()
recommendations = []

for _, row in comparison_df.iterrows():
    if row["Model"] == "Linear Regression":
        recommendations.append("Selected")
    elif (best_score - linear_score) <= 0.50:
        recommendations.append("Evaluated")
    else:
        recommendations.append("Compared")

comparison_df["Recommendation"] = recommendations

print("\n" + "=" * 78)
print(" MODEL COMPARISON")
print("=" * 78)

print(
    comparison_df.to_string(
        index=False,
        formatters={
            "R² Score (%)": lambda x: f"{x:.2f}%",
            "MAE (days)": lambda x: f"{x:.3f}",
            "RMSE (days)": lambda x: f"{x:.3f}",
            "Improvement vs Baseline": lambda x: "Baseline" if abs(x) < 0.00001 else f"{x:+.2f}%"
        }
    )
)

# ================================================================
# SAVE MODEL ARTIFACTS
# ================================================================

deployment_model = LinearRegression()
deployment_model.fit(X_train_final, y_train_full)

joblib.dump(deployment_model, "gasguard_model.pkl")
joblib.dump(scaler, "gasguard_scaler.pkl")

# Save duplicates in gasguard_models/ folder too for index.html compatibility
joblib.dump(deployment_model, "gasguard_models/gasguard_model.pkl")
joblib.dump(scaler, "gasguard_models/gasguard_scaler.pkl")

comparison_df.to_csv("model_comparison.csv", index=False)

# Save metadata.json matching original format
metadata = {
    'model_name': 'GasGuard Linear Regression',
    'version': '2.1.0',
    'training_date': time.strftime('%Y-%m-%d %H:%M:%S'),
    'student_name': 'WALP Harsha',
    'registration_no': 'D/ENG/24/0095/ET',
    'dataset_size': len(df),
    'features_used': FEATURES,
    'performance': {
        'r2_score': float(test_r2),
        'mean_absolute_error_days': float(mae),
        'root_mean_square_error_days': float(rmse),
        'mean_absolute_percentage_error': float(mape),
        'cross_validation_mean': float(cv_scores.mean())
    },
    'model_coefficients': {
        'intercept': float(original_intercept),
        **{name: float(coef) for name, coef in zip(FEATURES, original_coefficients)}
    },
    'comparison': comparison_results
}

with open('gasguard_metadata.json', 'w') as f:
    json.dump(metadata, f, indent=2)

with open('gasguard_models/gasguard_metadata.json', 'w') as f:
    json.dump(metadata, f, indent=2)

print("\n✓ Metadata files saved successfully.")

# Upload metrics to Firebase Realtime Database
def upload_metrics_to_firebase(metadata_dict):
    print("\n🌐 Uploading model metrics to Firebase Realtime Database...")
    import urllib.request
    import urllib.error
    import ssl
    
    FIREBASE_API_KEY = "AIzaSyAnUdXb9dJzbqOF90RwgAxMdtHJzfchaSg"
    FIREBASE_DB_URL = "https://gasguardkdu-default-rtdb.asia-southeast1.firebasedatabase.app"
    FIREBASE_EMAIL = "gasgurd@gmail.com"
    FIREBASE_PASSWORD = "gasgurd"

    auth_url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={FIREBASE_API_KEY}"
    payload = {
        "email": FIREBASE_EMAIL,
        "password": FIREBASE_PASSWORD,
        "returnSecureToken": True
    }
    
    headers = {'Content-Type': 'application/json'}
    req_data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(auth_url, data=req_data, headers=headers, method='POST')
    
    context = None
    try:
        context = ssl._create_unverified_context()
    except Exception:
        pass
        
    try:
        with urllib.request.urlopen(req, timeout=10, context=context) as response:
            auth_data = json.loads(response.read().decode('utf-8'))
            id_token = auth_data["idToken"]
    except Exception as e:
        print(f"❌ Firebase Authentication failed: {e}")
        return

    perf = metadata_dict['performance']
    metrics_payload = {
        "r2Score": float(perf['r2_score'] * 100.0),
        "mae": float(perf['mean_absolute_error_days']),
        "rmse": float(perf['root_mean_square_error_days']),
        "mape": float(perf['mean_absolute_percentage_error']),
        "datasetSize": int(metadata_dict['dataset_size']),
        "crossValidationMean": float(perf['cross_validation_mean'] * 100.0)
    }
    
    db_url = f"{FIREBASE_DB_URL}/gasguard/metrics.json?auth={id_token}"
    req_data = json.dumps(metrics_payload).encode('utf-8')
    req = urllib.request.Request(db_url, data=req_data, headers=headers, method='PUT')
    
    try:
        with urllib.request.urlopen(req, timeout=10, context=context) as response:
            print("✅ Successfully updated gasguard/metrics node in Firebase RTDB!")
            print(json.dumps(metrics_payload, indent=2))
    except Exception as e:
        print(f"❌ Failed to update metrics in Firebase: {e}")

try:
    upload_metrics_to_firebase(metadata)
except Exception as e:
    print(f"❌ Firebase upload failed: {e}")

# ================================================================
# PLOT 1 - TRAINING LOSS
# ================================================================

plt.figure(figsize=(9, 5))
plt.plot(range(1, EPOCHS + 1), train_losses, label="Training MSE")
plt.plot(range(1, EPOCHS + 1), validation_losses, label="Validation MSE")
plt.xlabel("Epoch")
plt.ylabel("Mean Squared Error")
plt.title("Linear Regression Training Convergence")
plt.legend()
plt.grid(alpha=0.3)
plt.savefig("gasguard_plots/01_training_convergence.png", dpi=300, bbox_inches="tight")
plt.savefig("01_training_convergence.png", dpi=300, bbox_inches="tight")
plt.close()

# ================================================================
# PLOT 2 - ACTUAL VS PREDICTED
# ================================================================

plt.figure(figsize=(7, 7))
plt.scatter(y_test, y_test_pred, alpha=0.75)
minimum = min(y_test.min(), y_test_pred.min())
maximum = max(y_test.max(), y_test_pred.max())
plt.plot([minimum, maximum], [minimum, maximum], linestyle="--", label="Ideal Prediction (y = x)")
plt.xlabel("Actual Days Remaining")
plt.ylabel("Predicted Days Remaining")
plt.title("Actual vs Predicted LPG Depletion")
plt.legend()
plt.grid(alpha=0.3)
plt.savefig("gasguard_plots/02_actual_vs_predicted.png", dpi=300, bbox_inches="tight")
plt.savefig("02_actual_vs_predicted.png", dpi=300, bbox_inches="tight")
plt.close()

# ================================================================
# PLOT 3 - RESIDUAL ANALYSIS
# ================================================================

residuals = y_test.to_numpy() - y_test_pred
plt.figure(figsize=(9, 5))
plt.scatter(y_test_pred, residuals)
plt.axhline(0, linestyle="--")
plt.xlabel("Predicted Days Remaining")
plt.ylabel("Residual Error (days)")
plt.title("Linear Regression Residual Analysis")
plt.grid(alpha=0.3)
plt.savefig("gasguard_plots/03_residual_analysis.png", dpi=300, bbox_inches="tight")
plt.savefig("03_residual_analysis.png", dpi=300, bbox_inches="tight")
plt.close()

# ================================================================
# PLOT 4 - FEATURE COEFFICIENTS
# ================================================================

coef_df = pd.DataFrame({
    "Feature": FEATURES,
    "Coefficient": original_coefficients
})
coef_df = coef_df.iloc[np.argsort(np.abs(coef_df["Coefficient"].values))]

plt.figure(figsize=(9, 5))
plt.barh(coef_df["Feature"], coef_df["Coefficient"])
plt.xlabel("Regression Coefficient")
plt.title("Linear Regression Feature Coefficients")
plt.grid(axis="x", alpha=0.3)
plt.savefig("gasguard_plots/04_feature_coefficients.png", dpi=300, bbox_inches="tight")
plt.savefig("04_feature_coefficients.png", dpi=300, bbox_inches="tight")
plt.close()

# ================================================================
# PLOT 5 - MODEL COMPARISON
# ================================================================

plt.figure(figsize=(9, 5))
plt.bar(comparison_df["Model"], comparison_df["R² Score (%)"])
plt.ylabel("R² Score (%)")
plt.title("GasGuard Regression Model Comparison")
plt.xticks(rotation=15)
plt.grid(axis="y", alpha=0.3)
plt.savefig("gasguard_plots/05_model_comparison.png", dpi=300, bbox_inches="tight")
plt.savefig("05_model_comparison.png", dpi=300, bbox_inches="tight")
plt.close()

# ================================================================
# SAVE TEXT RESULTS FOR REPORT
# ================================================================

with open("gasguard_results.txt", "w", encoding="utf-8") as file:
    file.write("GASGUARD MODEL RESULTS\n\n")
    file.write("TRAINING CONVERGENCE\n")
    for epoch in [1, 25, 50, 75, 100]:
        i = epoch - 1
        file.write(
            f"Epoch {epoch}: MSE = {train_losses[i]:.4f}, "
            f"Validation R2 = {validation_scores[i]:.4f}, "
            f"Gradient Norm = {gradient_norms[i]:.6f}\n"
        )
    file.write("\nMODEL PERFORMANCE\n")
    file.write(f"Training R2: {train_r2*100:.2f}%\n")
    file.write(f"Testing R2: {test_r2*100:.2f}%\n")
    file.write(f"MAE: {mae:.3f} days\n")
    file.write(f"RMSE: {rmse:.3f} days\n")
    file.write(f"MAPE: {mape:.2f}%\n")
    file.write(f"5-Fold CV: {cv_scores.mean()*100:.2f}% ± {cv_scores.std()*100:.2f}%\n")
    file.write("\nFINAL EQUATION\n")
    file.write(equation)

# ================================================================
# FINISH
# ================================================================

print("\n" + "=" * 78)
print(" TRAINING COMPLETED SUCCESSFULLY")
print("=" * 78)

print("\nGenerated Files:")
print("✓ gasguard_model.pkl")
print("✓ gasguard_scaler.pkl")
print("✓ model_comparison.csv")
print("✓ gasguard_results.txt")
print("✓ gasguard_metadata.json")
print("✓ 01_training_convergence.png")
print("✓ 02_actual_vs_predicted.png")
print("✓ 03_residual_analysis.png")
print("✓ 04_feature_coefficients.png")
print("✓ 05_model_comparison.png")
