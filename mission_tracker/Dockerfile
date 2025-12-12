# Use the official Python image from the Docker Hub
FROM python:3.12-slim

# Copy the requirements file into the container
COPY src/ .
COPY requirements.txt .

# Install the dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Expose the port the app runs on
EXPOSE 8000

# Define the command to run the application
CMD ["python", "app.py"]