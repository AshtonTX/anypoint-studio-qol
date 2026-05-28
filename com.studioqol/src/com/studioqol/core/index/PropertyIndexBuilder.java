package com.studioqol.core.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;

/**
 * Builds a {@link PropertyIndex} for a Mule project from {@code src/main/resources/envs/*.properties}
 * (per-environment values) and {@code src/main/mule/*.xml} (inline globals + secure-properties config).
 * Env files are self-contained per the convention; a key in env files outranks a same-named global.
 */
public final class PropertyIndexBuilder {

	private static final String ENVS_DIR = "src/main/resources/envs";
	private static final String MULE_DIR = "src/main/mule";
	private static final List<String> ENV_ORDER = Arrays.asList("local", "test", "prod", "example");

	public PropertyIndex build(IProject project) {
		Map<String, PropertyDefinition> byKey = new LinkedHashMap<>();
		List<String> environments = new ArrayList<>();
		try {
			List<MuleXmlParser.GlobalProp> globals = new ArrayList<>();
			Set<String> secureFiles = new HashSet<>();
			collectFromMuleXml(project, globals, secureFiles);

			Map<String, Map<String, EnvValue>> perKey = new LinkedHashMap<>();
			Set<String> envNames = new TreeSet<>();
			Map<String, IFile> secureSources = new HashMap<>();
			collectFromEnvFiles(project, secureFiles, perKey, envNames, secureSources);

			environments = orderEnvironments(envNames);

			for (Map.Entry<String, Map<String, EnvValue>> e : perKey.entrySet()) {
				byKey.put(e.getKey(), PropertyDefinition.envFile(e.getKey(), e.getValue()));
			}
			for (Map.Entry<String, IFile> sf : secureSources.entrySet()) {
				for (Map.Entry<String, PropertiesFileParser.Entry> e : PropertiesFileParser
						.parse(sf.getValue()).entrySet()) {
					byKey.putIfAbsent(e.getKey(), PropertyDefinition.secure(e.getKey(), e.getValue().location));
				}
			}
			// Globals are the lowest-precedence tier; do not override env-file/secure keys.
			for (MuleXmlParser.GlobalProp gp : globals) {
				byKey.putIfAbsent(gp.name, PropertyDefinition.global(gp.name, gp.value, gp.location));
			}
		} catch (Exception e) {
			// Return whatever we managed to build; an empty index is valid.
		}
		return new PropertyIndex(byKey, environments);
	}

	private void collectFromMuleXml(IProject project, List<MuleXmlParser.GlobalProp> globals,
			Set<String> secureFiles) throws Exception {
		IFolder dir = project.getFolder(new Path(MULE_DIR));
		if (!dir.exists()) {
			return;
		}
		for (IResource r : dir.members()) {
			if (r.getType() == IResource.FILE && "xml".equalsIgnoreCase(((IFile) r).getFileExtension())) {
				MuleXmlParser.Result res = MuleXmlParser.parse((IFile) r);
				globals.addAll(res.globals);
				secureFiles.addAll(res.secureFileBaseNames);
			}
		}
	}

	private void collectFromEnvFiles(IProject project, Set<String> secureFiles,
			Map<String, Map<String, EnvValue>> perKey, Set<String> envNames,
			Map<String, IFile> secureSources) throws Exception {
		IFolder dir = project.getFolder(new Path(ENVS_DIR));
		if (!dir.exists()) {
			return;
		}
		for (IResource r : dir.members()) {
			if (r.getType() != IResource.FILE) {
				continue;
			}
			IFile f = (IFile) r;
			if (!"properties".equalsIgnoreCase(f.getFileExtension())) {
				continue;
			}
			Map<String, PropertiesFileParser.Entry> entries = PropertiesFileParser.parse(f);
			// A file is a secure source if a secure-properties:config names it, OR it holds encrypted
			// ![...] values (robust when the XML's namespaced lookup misses the config element).
			if (secureFiles.contains(f.getName()) || hasEncrypted(entries)) {
				secureSources.put(f.getName(), f);
				continue;
			}
			String env = stripExtension(f.getName());
			envNames.add(env);
			for (Map.Entry<String, PropertiesFileParser.Entry> e : entries.entrySet()) {
				PropertiesFileParser.Entry entry = e.getValue();
				perKey.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>())
						.put(env, new EnvValue(env, entry.value, entry.location, entry.state));
			}
		}
	}

	private static List<String> orderEnvironments(Set<String> envs) {
		List<String> ordered = new ArrayList<>();
		for (String pref : ENV_ORDER) {
			if (envs.contains(pref)) {
				ordered.add(pref);
			}
		}
		List<String> rest = new ArrayList<>(envs);
		rest.removeAll(ordered);
		Collections.sort(rest);
		ordered.addAll(rest);
		return ordered;
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot >= 0 ? name.substring(0, dot) : name;
	}

	private static boolean hasEncrypted(Map<String, PropertiesFileParser.Entry> entries) {
		for (PropertiesFileParser.Entry entry : entries.values()) {
			if (entry.state == EnvValue.State.SECURE) {
				return true;
			}
		}
		return false;
	}
}
