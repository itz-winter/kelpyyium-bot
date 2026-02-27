# Kelpyyium Discord Bot

Kelpyyium is a Java-based Discord bot built using the JDA (Java Discord API). This bot provides various features such as global chat relay, moderation tools, and utility commands.

## Prerequisites

Before building and running the bot, ensure you have the following installed:

1. **Java Development Kit (JDK) 17**
   - Download and install JDK 17 from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/javase-downloads.html).
   - Verify the installation by running:
     ```powershell
     java -version
     ```
     Ensure the output shows Java 17.

2. **Apache Maven**
   - Download and install Maven from [Maven's official website](https://maven.apache.org/download.cgi).
   - Verify the installation by running:
     ```powershell
     mvn -version
     ```
     Ensure Maven is properly installed and added to your system's PATH.

## Building the Project

Follow these steps to build the project:

1. **Clone the Repository**
   - Clone the repository to your local machine:
     ```powershell
     git clone <repository-url>
     cd kelpyyium
     ```

2. **Install Dependencies**
   - Maven will automatically download and install the required dependencies during the build process.

3. **Build the Project**
   - Run the following command to build the project and create a fat JAR:
     ```powershell
     mvn clean package
     ```
   - The output JAR file will be located in the `target` directory, named something like `discord-server-bot-1.0.0.jar`.

## Running the Bot

1. **Navigate to the Target Directory**
   - Change to the `target` directory:
     ```powershell
     cd target
     ```

2. **Run the JAR File**
   - Execute the JAR file using the following command:
     ```powershell
     java -jar kelpyyium-1.0.0.jar
     ```

## Configuration

- The bot's configuration file is `config.json`, located in the root directory. Ensure you update this file with your bot token and other necessary settings before running the bot.

## Logging

- Logs are stored in the `logs` directory. You can configure logging settings in `src/main/resources/logback.xml`.

## Features

- **Global Chat Relay**: Seamlessly relay messages across multiple servers.
- **Moderation Tools**: Includes commands for managing users and maintaining server integrity.
- **Utility Commands**: Offers various helpful commands for server management.
- **Reply Handling**: Relayed replies include clickable jump links to the original message.
- **Cross-Server Message Deletion**: Deletes relayed copies when a message is deleted in one server.

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests to improve the bot.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
