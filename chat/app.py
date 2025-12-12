import requests
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
# Configura Flask-CORS per includere le intestazioni CORS appropriate
CORS(app, resources={r"/*": {"origins": "*"}}, supports_credentials=True)

app.config["CORS_HEADERS"] = "Content-Type"

@app.route("/hello", methods=["GET"])
def hello():
    return jsonify({"message": "Hello, World!"})

@app.route("/generate", methods=["POST", "OPTIONS"])
def generate():

    if request.method == "OPTIONS":
        return _build_cors_preflight_response()

    data = request.get_json()
    prompt = data.get("prompt", "")

    if not prompt:
        return jsonify({"error": "Prompt is required"}), 400

    # Genera la risposta utilizzando il modello personalizzato
    response = requests.post(
        "http://192.168.250.20:11434/api/generate",
        json={"model": "CustomGemma9", "prompt": prompt, "stream": False},
        timeout=600,
    )
    response_json = response.json()
    if response_json.get("response"):
        actual_response = response.json()["response"]
    else:
        actual_response = response.json()["error"]

    return _corsify_actual_response(jsonify({"response": actual_response}))


def _build_cors_preflight_response():
    response = jsonify({"status": "success"})
    response.headers.add("Allow-Control-Allow-Origin", "*")
    response.headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization")
    response.headers.add("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
    return response


def _corsify_actual_response(response):
    response.headers.add("Access-Control-Allow-Origin", "*")
    return response


if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0")
