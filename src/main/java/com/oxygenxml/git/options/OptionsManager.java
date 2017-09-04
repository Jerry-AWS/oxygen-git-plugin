package com.oxygenxml.git.options;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import com.oxygenxml.git.WorkspaceAccessPlugin;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;

/**
 * Used to save and load different plugin options
 * 
 * @author Beniamin Savu
 *
 */
public class OptionsManager {
	/**
	 * Logger for logging.
	 */
	private static Logger logger = Logger.getLogger(OptionsManager.class);

	/**
	 * The filename in which all the options are saved
	 */
	private static final String PLUGIN_OPTIONS_FILENAME = "Options.xml";

	/**
	 * Constant for how many commits messages to be saved
	 */
	private static final int PREVIOUSLY_COMMITED_MESSAGES = 7;

	/**
	 * Constant for how many project paths that have been tested for git to store
	 */
	private static final int MAXIMUM_PROJECTS_TESTED = 10;

	/**
	 * All Repositories that were selected by the user with their options
	 */
	private Options options = null;

	/**
	 * Singleton instance.
	 */
	private static OptionsManager instance;

	/**
	 * Gets the singleton instance
	 * 
	 * @return singleton instance
	 */
	public static OptionsManager getInstance() {
		if (instance == null) {
			instance = new OptionsManager();
		}
		return instance;
	}

	/**
	 * Uses JAXB to load all the selected repositories from the users in the
	 * repositoryOptions variable
	 */
	private void loadOptions() {
		if (options == null) {
			options = new Options();
			try {
				JAXBContext jaxbContext = JAXBContext.newInstance(Options.class);
				Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
				if (WorkspaceAccessPlugin.getInstance() == null) {
					File optionsFile = getOptionsFile();
					if (optionsFile.exists()) {
						options = (Options) jaxbUnmarshaller.unmarshal(optionsFile);
					} else {
						logger.warn("Options file doesn't exist:" + optionsFile.getAbsolutePath());
					}
				} else {
					String option = PluginWorkspaceProvider.getPluginWorkspace().getOptionsStorage()
							.getOption("MY_PLUGIN_OPTIONS", null);

					if (option != null) {
						options = (Options) jaxbUnmarshaller.unmarshal(new StringReader(
								PluginWorkspaceProvider.getPluginWorkspace().getXMLUtilAccess().unescapeAttributeValue(option)));
					}
				}
			} catch (JAXBException e) {
				logger.warn("Options not loaded: " + e, e);
			}

		}
	}

	/**
	 * Creates the the options file and returns it
	 * 
	 * @return the options file
	 */
	private File getOptionsFile() {
		File baseDir = null;
		if (WorkspaceAccessPlugin.getInstance() != null) {
			baseDir = WorkspaceAccessPlugin.getInstance().getDescriptor().getBaseDir();
		} else {
			baseDir = new File("src/main/resources");
		}
		return new File(baseDir, PLUGIN_OPTIONS_FILENAME);
	}

	/**
	 * Uses JAXB to save all the selected repositories from the users in the
	 * repositoryOptions variable
	 */
	private void saveOptions() {
		try {

			JAXBContext jaxbContext = JAXBContext.newInstance(Options.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			if (WorkspaceAccessPlugin.getInstance() == null) {
				jaxbMarshaller.marshal(options, getOptionsFile());
			} else {
				StringWriter stringWriter = new StringWriter();
				jaxbMarshaller.marshal(options, stringWriter);
				PluginWorkspaceProvider.getPluginWorkspace().getOptionsStorage().setOption("MY_PLUGIN_OPTIONS",
						PluginWorkspaceProvider.getPluginWorkspace().getXMLUtilAccess().escapeTextValue(stringWriter.toString()));
			}
		} catch (JAXBException e1) {
			e1.printStackTrace();
		}

	}

	/**
	 * Retrieves the repository selection list
	 * 
	 * @return a set with the repository options
	 */
	public Set<String> getRepositoryEntries() {
		loadOptions();

		return options.getRepositoryLocations().getLocations();
	}

	/**
	 * Saves the given repository options
	 * 
	 * @param repositoryOption
	 *          - options to be saved
	 */
	public void addRepository(String repositoryOption) {
		loadOptions();

		options.getRepositoryLocations().getLocations().add(repositoryOption);
		saveOptions();
	}

	/**
	 * Saves the last selected repository from the user
	 * 
	 * @param path
	 *          - the path to the selected repository
	 */
	public void saveSelectedRepository(String path) {
		loadOptions();
		options.setSelectedRepository(path);

		saveOptions();
	}

	/**
	 * Loads the last selected repository from the user
	 * 
	 * @return the path to the selected repository
	 */
	public String getSelectedRepository() {
		loadOptions();

		return options.getSelectedRepository();
	}

	public void removeSelectedRepository(String path) {
		loadOptions();
		options.getRepositoryLocations().getLocations().remove(path);

		saveOptions();
	}

	/**
	 * Saves the user credentials for git push and pull
	 * 
	 * @param userCredentials
	 *          - the credentials to be saved
	 */
	public void saveGitCredentials(UserCredentials userCredentials) {
		loadOptions();
		
		UserCredentials uc = new UserCredentials();
		String encryptedPassword = ((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace())
				.getUtilAccess().encrypt(uc.getPassword());
		uc.setPassword(encryptedPassword);
		uc.setUsername(userCredentials.getUsername());
		uc.setHost(userCredentials.getHost());
		
		List<UserCredentials> credentials = options.getUserCredentialsList().getCredentials();
		for (Iterator<UserCredentials> iterator = credentials.iterator(); iterator.hasNext();) {
			UserCredentials alreadyHere = (UserCredentials) iterator.next();
			if (alreadyHere.getHost().equals(uc.getHost())) {
				// Replace.
				iterator.remove();
				break;
			}
		}

		credentials.add(uc);

		saveOptions();

	}

	/**
	 * Loads the user credentials for git push and pull
	 * 
	 * @return the credentials
	 */
	public UserCredentials getGitCredentials(String host) {
		loadOptions();

		List<UserCredentials> userCredentialsList = options.getUserCredentialsList().getCredentials();
		String username = "";
		String password = "";
		for (UserCredentials credential : userCredentialsList) {
			if (host.equals(credential.getHost())) {
				username = credential.getUsername();
				password = credential.getPassword();
				break;
			}
		}

		String decryptedPassword = ((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace())
				.getUtilAccess().decrypt(password);
		if (decryptedPassword == null) {
			decryptedPassword = "";
		}

		UserCredentials userCredentials = new UserCredentials(username, decryptedPassword, host);
		return userCredentials;
	}

	/**
	 * Loads the last PREVIOUSLY_COMMITED_MESSAGES massages
	 * 
	 * @return a list with the previously committed messages
	 */
	public List<String> getPreviouslyCommitedMessages() {
		loadOptions();

		return options.getCommitMessages().getMessages();

	}

	/**
	 * Saves the last commit message and promotes it in front of the list
	 * 
	 * @param commitMessage
	 *          - the last commitMessage
	 */
	public void saveCommitMessage(String commitMessage) {
		loadOptions();

		List<String> messages = options.getCommitMessages().getMessages();
		if (messages.contains(commitMessage)) {
			messages.remove(commitMessage);
		}
		messages.add(0, commitMessage);
		if (messages.size() > PREVIOUSLY_COMMITED_MESSAGES) {
			messages.remove(messages.size() - 1);
		}
		options.getCommitMessages().setMessages(messages);

		saveOptions();
	}

	/**
	 * Gets the last MAXIMUM_PROJECTS_TESTED from the project view tested to be
	 * git repositories
	 * 
	 * @return a list with the last MAXIMUM_PROJECTS_TESTED paths
	 */
	public List<String> getProjectsTestedForGit() {
		loadOptions();

		return options.getPrjectsTestsForGit().getPaths();
	}

	/**
	 * Saves the given project path from the project view
	 * 
	 * @param projectPath
	 *          - the project path to be saved
	 */
	public void saveProjectTestedForGit(String projectPath) {
		loadOptions();

		List<String> projectsPath = options.getPrjectsTestsForGit().getPaths();
		projectsPath.add(projectPath);
		if (projectsPath.size() > MAXIMUM_PROJECTS_TESTED) {
			projectsPath.remove(0);
		}
		options.getPrjectsTestsForGit().setPaths(projectsPath);

		saveOptions();
	}
}
