FROM python:3.11-slim

# Imposta la directory di lavoro
WORKDIR /app

# Copia i file requirements
COPY requirements.txt .

# Installa le dipendenze
RUN pip install --no-cache-dir -r requirements.txt

# Copia il codice dell'applicazione
COPY src/ .

# Espone la porta 2000
EXPOSE 2000

# Comando per avviare l'applicazione
CMD ["gunicorn", "--bind", "0.0.0.0:2000", "--workers", "4", "app:app"]