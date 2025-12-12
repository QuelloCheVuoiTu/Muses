package questing;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/questing")
public class App extends Application {
	public Set<Class<?>> getClasses() {
		Set<Class<?>> classes = new HashSet<Class<?>>();
		classes.add(questing.controller.ArtworkController.class);
		classes.add(questing.controller.ChatController.class);
		classes.add(questing.controller.HealthController.class);
		classes.add(questing.controller.LocationController.class);
		classes.add(questing.controller.MuseumController.class);
		classes.add(questing.controller.OSRMController.class);
		classes.add(questing.controller.QuestController.class);
		classes.add(questing.controller.QuestManagerController.class);
		classes.add(questing.controller.UserController.class);
		return classes;
	}
}
