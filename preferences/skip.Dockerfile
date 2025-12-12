FROM python:3.11-slim

# Imposta la directory di lavoro
WORKDIR /app

# Copia i file dei requisiti
COPY requirements.txt .

# Installa le dipendenze
RUN pip install --no-cache-dir -r requirements.txt

# Copia il codice dell'applicazione
COPY app.py .

# Crea un utente non-root per sicurezza
RUN useradd --create-home --shell /bin/bash app && chown -R app:app /app
USER app

# Espone la porta 7000
EXPOSE 7000

# Comando per avviare l'applicazione con Gunicorn
CMD ["gunicorn", "--bind", "0.0.0.0:7000", "--workers", "4", "app:app"]