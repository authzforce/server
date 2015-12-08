/**
 * Copyright (C) 2012-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package com.thalesgroup.authzforce.rest;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.beans.ConstructorProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotNull;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.validation.Schema;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import com.thalesgroup.appsec.util.Utils;
import com.thalesgroup.authz.model._3.AttributeFinders;
import com.thalesgroup.authz.model._3.PolicySets;
import com.thalesgroup.authz.model._3_0.resource.Properties;
import com.thalesgroup.authzforce.core.PdpExtensionLoader;
import com.thalesgroup.authzforce.core.PdpModelHandler;

public class SecurityDomainManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityDomainManager.class);

	private static final Class<?>[] DEFAULT_JAXB_CTX_BOUND_CLASSES = { PolicySets.class, AttributeFinders.class,
			Properties.class };

	private final Path domainsRootDir;

	/**
	 * Maps domainId to domain
	 */
	private final ConcurrentMap<UUID, SecurityDomain> domainMap = new ConcurrentHashMap<>();

	private final File domainTmplDir;

	private final Schema schema;

	private final JAXBContext jaxbCtx;

	private final PdpModelHandler pdpModelHandler;

	private final ScheduledExecutorService domainsFolderSyncTaskScheduler;

	private int domainsFolderSyncIntervalSec;

	private final WatchService domainsFolderWatcher;

	@SuppressWarnings("unchecked")
	private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	private class DomainsFolderSyncTask implements Runnable {
		private final Map<Path, WatchEvent.Kind<?>> domainFolderToEventMap = new HashMap<>();
		private final Map<WatchKey, Path> domainsFolderWatchKeys = new HashMap<>();

		/**
		 * Register the given directory with the WatchService
		 */
		private void addWatchedDirectory(Path dir) {
			final WatchKey key;
			try {
				key = dir.register(domainsFolderWatcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			} catch (IOException ex) {
				throw new RuntimeException(
						"Failed to register directory '" + dir + "' with WatchService for synchronization", ex);
			}

			if (LOGGER.isDebugEnabled()) {
				final Path prev = this.domainsFolderWatchKeys.get(key);
				if (prev == null) {
					LOGGER.debug("register watch key: {}", dir);
				} else {
					if (!dir.equals(prev)) {
						LOGGER.debug("update watch key: {} -> {}", prev, dir);
					}
				}
			}

			this.domainsFolderWatchKeys.put(key, dir);
		}

		@Override
		public void run() {
			try {
				LOGGER.debug("Executing synchronization task...");
				WatchKey key;
				// try {
				// key = watcher.take();
				// if(key == null) {
				// continue;
				// }
				// } catch (InterruptedException x) {
				// // throw new RuntimeException(x);
				// return;
				// }
				// poll all pending watch keys
				while ((key = domainsFolderWatcher.poll()) != null) {

					final Path dir = domainsFolderWatchKeys.get(key);
					if (dir == null) {
						LOGGER.error("Watch key does not match any registered directory");
						continue;
					}

					LOGGER.debug("Processing watch key for path: {}", dir);

					for (final WatchEvent<?> event : key.pollEvents()) {
						final WatchEvent.Kind<?> kind = event.kind();

						if (kind == OVERFLOW) {
							LOGGER.error(
									"Some watch event might have been lost or discarded. Consider restarting the application to force reset synchronization state and reduce the sync interval.");
							continue;
						}

						// Context for directory entry event is the file name of
						// entry
						final WatchEvent<Path> ev = cast(event);
						final Path childRelativePath = ev.context();
						final Path childAbsPath = dir.resolve(childRelativePath);

						// print out event
						LOGGER.info("Domains folder change detected: {}: {}", event.kind().name(), childRelativePath);

						// if directory is created, and watching recursively,
						// then
						// register it and its sub-directories
						if (/* recursive && */(kind == ENTRY_CREATE)) {
							if (Files.isDirectory(childAbsPath, NOFOLLOW_LINKS)) {
								// registerAll(child);
								addWatchedDirectory(childAbsPath);
							}
						}

						// MONITORING DOMAIN FOLDERS
						if (dir.equals(domainsRootDir)) {
							// child of root folder (domains) created or deleted
							// (ignore modify at
							// this
							// level)
							if ((kind == ENTRY_CREATE && Files.isDirectory(childAbsPath, NOFOLLOW_LINKS))
									|| kind == ENTRY_DELETE) {
								domainFolderToEventMap.put(childAbsPath, kind);
							}
						} else {
							/*
							 * modify on subfolder (domain) If no CREATE event
							 * already registered in map, register MODIFY
							 */
							Kind<?> eventKind = domainFolderToEventMap.get(dir);
							if (eventKind != ENTRY_CREATE) {
								domainFolderToEventMap.put(dir, ENTRY_MODIFY);
							}
						}
					}

					// reset key and remove from set if directory no longer
					// accessible
					boolean valid = key.reset();
					if (!valid) {
						domainsFolderWatchKeys.remove(key);

						// all directories are inaccessible
						if (domainsFolderWatchKeys.isEmpty()) {
							break;
						}
					}
				}

				// do the actions according to map
				LOGGER.debug("Synchronization events to be handled: {}", domainFolderToEventMap);
				for (final Entry<Path, Kind<?>> domainFolderToEventEntry : domainFolderToEventMap.entrySet()) {
					final Path domainDir = domainFolderToEventEntry.getKey();
					final Kind<?> eventKind = domainFolderToEventEntry.getValue();
					// domainFolder name is assumed to be a domain ID
					final String domainFoldername = domainDir.getFileName().toString();
					final UUID domainId = UUID.fromString(domainFoldername);
					if (eventKind == ENTRY_CREATE || eventKind == ENTRY_MODIFY) {
						/*
						 * synchonized block makes sure no other thread is
						 * messing with the domains directory while we
						 * synchronize it to domainMap. See also method
						 * #add(Properties)
						 */
						synchronized (domainsRootDir) {
							final SecurityDomain secDomain = domainMap.get(domainId);
							// Force creation if domain does not exist, else
							// reload
							if (secDomain == null) {
								// force creation
								LOGGER.info(
										"Sync event '{}' on domain '{}: domain not found in memory -> loading new domain from folder '{}'",
										new Object[] { eventKind, domainId, domainDir });
								final SecurityDomain newSecDomain = new SecurityDomain(domainDir.toFile(), jaxbCtx,
										schema, pdpModelHandler, null);
								domainMap.put(domainId, newSecDomain);
							} else {
								LOGGER.info(
										"Sync event '{}' on domain '{}: domain found in memory -> reloading from folder '{}'",
										new Object[] { eventKind, domainId, domainDir });
								secDomain.reloadPDP();
							}
						}
					} else if (eventKind == ENTRY_DELETE) {
						LOGGER.info("Sync event '{}' on domain '{}: deleting if exists in memory",
								new Object[] { eventKind, domainId, domainDir });
						domainMap.remove(domainId);
					}
				}
				LOGGER.debug("Synchronization done.");

				domainFolderToEventMap.clear();
			} catch (Exception e) {
				LOGGER.error("Error occurred during domains folder synchronization task", e);
			}
		}
	}

	/**
	 * @param domainsRoot
	 *            root directory of the configuration data of security domains,
	 *            one subdirectory per domain
	 * @param domainTmpl
	 *            domain template directory; directories of new domains are
	 *            created from this template
	 * @param domainsSyncIntervalSec
	 *            how often (in seconds) the synchronization of managed domains
	 *            (in memory) with the domain subdirectories in the
	 *            <code>domainsRoot</code> directory (on disk) is done. If
	 *            <code>domainSyncInterval</code> > 0, every
	 *            <code>domainSyncInterval</code>, the managed domains (loaded
	 *            in memory) are updated if any change has been detected in the
	 *            <code>domainsRoot</code> directory in this interval (since
	 *            last sync). To be more specific, <i>any change</i> here means
	 *            any creation/deletion/modification of a domain folder
	 *            (modification means: any file changed within the folder). If
	 *            <code>domainSyncInterval</code> &lt;= 0, synchronization is
	 *            disabled.
	 * @param schema
	 *            XML schema for validating XML configurations of domains
	 *            (properties, attribute finders, policy sets, etc.)
	 * @param pdpModelHandler
	 *            PDP configuration model handler
	 */
	@ConstructorProperties({ "domainsRoot", "domainTmpl", "domainsSyncIntervalSec", "schema", "pdpModelHandler" })
	public SecurityDomainManager(@NotNull Resource domainsRoot, @NotNull Resource domainTmpl,
			int domainsSyncIntervalSec, @NotNull Schema schema, PdpModelHandler pdpModelHandler) {
		this.schema = schema;
		this.pdpModelHandler = pdpModelHandler;

		final List<Class<?>> jaxbClassesToBeBound = new ArrayList<>(Arrays.asList(DEFAULT_JAXB_CTX_BOUND_CLASSES));
		jaxbClassesToBeBound.addAll(PdpExtensionLoader.getExtensionJaxbClasses());
		try {
			jaxbCtx = JAXBContext.newInstance(jaxbClassesToBeBound.toArray(new Class<?>[jaxbClassesToBeBound.size()]));
		} catch (JAXBException e1) {
			throw new RuntimeException("Error creating JAXB context for (un)marshalling XML configurations", e1);
		}

		// Validate domainsRoot arg
		if (!domainsRoot.exists()) {
			throw new IllegalArgumentException(
					"'domainsRoot' resource does not exist: " + domainsRoot.getDescription());
		}

		final String ioExMsg = "Cannot resolve 'domainsRoot' resource '" + domainsRoot.getDescription()
				+ "' as a file on the file system";
		File domainsRootFile = null;
		try {
			domainsRootFile = domainsRoot.getFile();
		} catch (IOException e) {
			throw new IllegalArgumentException(ioExMsg, e);
		}

		Utils.checkFile("File defined by SecurityDomainManager parameter 'domainsRoot'", domainsRootFile, true, true);
		this.domainsRootDir = domainsRootFile.toPath();

		// Validate domainTmpl directory arg
		if (!domainTmpl.exists()) {
			throw new IllegalArgumentException("'domainTmpl' resource does not exist: " + domainTmpl.getDescription());
		}

		final String ioExMsg2 = "Cannot resolve 'domainTmpl' resource '" + domainTmpl.getDescription()
				+ "' as a file on the file system";
		File domainTmplFile = null;
		try {
			domainTmplFile = domainTmpl.getFile();
		} catch (IOException e) {
			throw new IllegalArgumentException(ioExMsg2, e);
		}

		Utils.checkFile("File defined by SecurityDomainManager parameter 'domainTmpl'", domainTmplFile, true, false);
		this.domainTmplDir = domainTmplFile;

		// Initialize endUserDomains and register their folders to the
		// WatchService for monitoring
		// them at the same time
		final DomainsFolderSyncTask syncTask;
		if (domainsSyncIntervalSec > 0) {
			// Sync enabled
			WatchService fsWatchService = null;
			try {
				fsWatchService = FileSystems.getDefault().newWatchService();
			} catch (IOException e) {
				throw new RuntimeException(
						"Failed to create a WatchService for watching directory changes to the domains on the filesystem",
						e);
			}

			if (fsWatchService == null) {
				throw new RuntimeException(
						"Failed to create a WatchService for watching directory changes to the domains on the filesystem");
			}

			this.domainsFolderWatcher = fsWatchService;
			syncTask = new DomainsFolderSyncTask();
			syncTask.addWatchedDirectory(this.domainsRootDir);
		} else {
			this.domainsFolderWatcher = null;
			syncTask = null;
		}

		LOGGER.debug("Looking for domain sub-directories in directory {}", domainsRootDir);
		try (final DirectoryStream<Path> dirStream = Files.newDirectoryStream(domainsRootDir)) {
			for (final Path domainPath : dirStream) {
				LOGGER.debug("Checking domain in file {}", domainPath);
				if (!Files.isDirectory(domainPath)) {
					LOGGER.warn("Ignoring invalid domain file {} (not a directory)", domainPath);
					continue;
				}
				final UUID domainId = UUID.fromString(domainPath.getFileName().toString());
				try {
					final SecurityDomain domain = new SecurityDomain(domainPath.toFile(), this.jaxbCtx, this.schema,
							this.pdpModelHandler, null);
					domainMap.put(domainId, domain);
				} catch (JAXBException e) {
					throw new RuntimeException(
							"Syntax error inside your domain configuration file (domain: " + domainId + ")", e);
				}

				if (syncTask != null) {
					syncTask.addWatchedDirectory(domainPath);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to scan files in the domains root directory '" + domainsRootDir
					+ "' looking for domain directories", e);
		}

		/*
		 * No error occurred, we can start new thread for watching/syncing
		 * domains safely now if sync interval > 0
		 */
		if (syncTask != null) {
			domainsFolderSyncTaskScheduler = Executors.newScheduledThreadPool(1);
			LOGGER.info("Scheduling periodic domains folder synchronization (initial delay={}s, period={}s)",
					domainsSyncIntervalSec, domainsSyncIntervalSec);
			domainsFolderSyncTaskScheduler.scheduleWithFixedDelay(syncTask, domainsSyncIntervalSec,
					domainsSyncIntervalSec, TimeUnit.SECONDS);
		} else {
			domainsFolderSyncTaskScheduler = null;
		}

		this.domainsFolderSyncIntervalSec = domainsSyncIntervalSec;
	}

	/**
	 * Stop domains folder synchronization thread (to be called by Spring when
	 * application stopped)
	 */
	public void stopDomainsSync() {
		if (domainsFolderSyncTaskScheduler != null) {
			LOGGER.info(
					"Requesting shutdown of scheduler of periodic domains folder synchronization. Waiting {}s for pending sync task to complete...",
					domainsFolderSyncIntervalSec);
			shutdownAndAwaitTermination(domainsFolderSyncTaskScheduler);
			try {
				LOGGER.info("Closing WatchService used for watching domains folder", domainsFolderSyncIntervalSec);
				domainsFolderWatcher.close();
			} catch (IOException e) {
				LOGGER.error("Failed to close WatchService. This may cause a memory leak.", e);
			}
		}
	}

	/*
	 * Code adapted from ExecutorService javadoc
	 */
	private void shutdownAndAwaitTermination(ExecutorService pool) {
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(domainsFolderSyncIntervalSec, TimeUnit.SECONDS)) {
				LOGGER.error(
						"Scheduler wait timeout ({}s) occurred before task could terminate after shutdown request.",
						domainsFolderSyncIntervalSec);
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!pool.awaitTermination(domainsFolderSyncIntervalSec, TimeUnit.SECONDS)) {
					LOGGER.error(
							"Scheduler wait timeout ({}s) occurred before task could terminate after shudownNow request.",
							domainsFolderSyncIntervalSec);
				}
			}
		} catch (InterruptedException ie) {
			LOGGER.error("Scheduler interrupted while waiting for sync task to complete", ie);
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Add domain
	 * 
	 * @param props
	 *            domain properties
	 * @return domain ID
	 * @throws IOException
	 *             Error creating domain (typically failed creating stored
	 *             domain data)
	 */
	public UUID add(@NotNull Properties props) throws IOException {
		final UUID domainId = Utils.generateUUID();
		synchronized (domainsRootDir) {
			// this should not happen if the UUID generator can be trusted, but
			// - hey - we never
			// know.
			if (this.domainMap.containsKey(domainId)) {
				throw new ConcurrentModificationException(
						"Generated domain ID conflicts (is same as) ID of existing domain (flawed domain UUID generator or ID generated in different way?): ID="
								+ domainId);
			}

			final Path domainDir = this.domainsRootDir.resolve(domainId.toString());
			final File domainDirFile = domainDir.toFile();
			if (Files.notExists(domainDir)) {
				/*
				 * Create/initialize new domain directory from domain template
				 * directory
				 */
				FileUtils.copyDirectory(this.domainTmplDir, domainDirFile);
			}

			try {
				final SecurityDomain domain = new SecurityDomain(domainDirFile, this.jaxbCtx, this.schema, this.pdpModelHandler, props);
				this.domainMap.put(domainId, domain);
			} catch (JAXBException e) {
				throw new RuntimeException("Syntax error inside your domain configuration file (domain: " + domainId + ")", e);
			}
		}

		return domainId;
	}

	/**
	 * Get IDs of all domains
	 * 
	 * @return domain IDs
	 */
	public Set<UUID> getDomainIds() {
		return Collections.unmodifiableSet(domainMap.keySet());
	}

	/**
	 * Returns domain of given ID
	 * 
	 * @param id
	 *            domain ID
	 * @return the domain identified by the specified id, or null if no domain
	 *         with such ID
	 */
	public SecurityDomain get(@NotNull UUID id) {
		return domainMap.get(id);
	}

	/**
	 * Removes domain from domain manager
	 * 
	 * @param id
	 *            domain ID
	 * @return properties of the removed domain associated with id, or null if
	 *         there was no domain for this ID.
	 * @throws IOException
	 *             deletion failed (typically failed deleting stored domain
	 *             data)
	 */
	public Properties remove(@NotNull UUID id) throws IOException {
		// Remove directory if any
		final Path domainDir = this.domainsRootDir.resolve(id.toString());
		final Properties domainProps;
		synchronized (domainsRootDir) {
			final SecurityDomain domain = domainMap.get(id);
			if (domain == null) {
				return null;
			}

			domainProps = domain.getProperties();
			if (Files.isDirectory(domainDir)) {
				FileUtils.deleteDirectory(domainDir.toFile());
			}

			domainMap.remove(id);
		}

		// Returns null if no domain for this id
		return domainProps;
	}

	/**
	 * @param id
	 *            domain ID
	 * @return true if domainManager has a domain with such ID
	 */
	public boolean containsId(@NotNull UUID id) {
		return domainMap.containsKey(id);
	}
}
