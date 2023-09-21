package com.deepgram.kvsdgintegrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Launcher {
	private static final Logger logger = LogManager.getLogger(Launcher.class);

	public static void main(String[] args) {
		logger.info("Launching integrator task");
		String integratorArgumentsJson = System.getenv("INTEGRATOR_ARGUMENTS");
		if (integratorArgumentsJson == null) {
			logger.error("This task expects an environment variable INTEGRATOR_ARGUMENTS");
			return;
		} else {
			logger.info("INTEGRATOR_ARGUMENTS=" + integratorArgumentsJson);
		}

		IntegratorArguments integratorArguments;
		try {
			integratorArguments = IntegratorArguments.fromJson(integratorArgumentsJson);
		} catch (JsonProcessingException e) {
			logger.error("Couldn't parse INTEGRATOR_ARGUMENTS: " + e);
			return;
		}

		String deepgramApiKey = System.getenv("DEEPGRAM_API_KEY");
		if (deepgramApiKey == null) {
			logger.error("This task expects an environment variable DEEPGRAM_API_KEY");
			return;
		}

		try {
			KvsToDgStreamer.startKvsToDgStreaming(integratorArguments, deepgramApiKey);
		} catch (Exception e) {
			logger.error("Task failed with exception: " + e);
		}
	}
}
