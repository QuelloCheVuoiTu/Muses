# Use the official OSRM backend image as the base
FROM ghcr.io/project-osrm/osrm-backend

# Set the working directory inside the container
WORKDIR /data

# Copy the OpenStreetMap data file into the container.
# Make sure "sud-latest.osm.pbf" is in the same directory as this Dockerfile.
COPY centro-latest.osm.pbf /data/centro-latest.osm.pbf

# Run the OSRM data processing steps
# osrm-extract: Extracts road network data from the PBF file using the foot profile
# The -p flag specifies the profile. We use the 'foot' profile already available in the base image.
RUN osrm-extract -p /opt/foot.lua /data/centro-latest.osm.pbf

# osrm-partition: Partitions the extracted data for efficient routing
RUN osrm-partition /data/centro-latest.osrm

# osrm-customize: Customizes the data for a specific routing algorithm (MLD in this case)
RUN osrm-customize /data/centro-latest.osrm

# Expose port 5000, which is the default port for the OSRM server
EXPOSE 5000

# Set the default command to run when a container is started from this image
# This starts the OSRM router with the specified algorithm and data file.
CMD ["osrm-routed", "--algorithm", "mld", "/data/centro-latest.osrm"]