/*
	Copyright (c) 2016 eBay Software Foundation.
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	    http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */
package org.apache.bark.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.bark.common.KeyValue;
import org.apache.bark.common.MetricType;
import org.apache.bark.common.ModelTypeConstants;
import org.apache.bark.common.ScheduleTypeConstants;
import org.apache.bark.common.SystemTypeConstants;
import org.apache.bark.dao.BarkMongoDAO;
import org.apache.bark.model.AssetLevelMetrics;
import org.apache.bark.model.AssetLevelMetricsDetail;
import org.apache.bark.model.BollingerBandsEntity;
import org.apache.bark.model.DQHealthStats;
import org.apache.bark.model.DQMetricsValue;
import org.apache.bark.model.DQModelEntity;
import org.apache.bark.model.MADEntity;
import org.apache.bark.model.ModelForFront;
import org.apache.bark.model.OverViewStatistics;
import org.apache.bark.model.SampleFilePathLKP;
import org.apache.bark.model.SampleOut;
import org.apache.bark.model.SystemLevelMetrics;
import org.apache.bark.model.SystemLevelMetricsList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
//import org.springframework.validation.annotation.Validated;


import com.mongodb.DBObject;

@Service
@Component("dqmetrics")
// @Validated
public class DQMetricsServiceImpl implements DQMetricsService {

	private static Logger logger = LoggerFactory
			.getLogger(DQMetricsServiceImpl.class);

	// @Autowired
	// private DQMetricsValuesDao dqMetricsDao;

	// @Autowired
	// private DataAssetDao dataAssetDao;

	@Autowired
	private DQModelService dqModelService;

	@Autowired
	private SubscribeService subscribeService;

	HashMap<String, String> modelSystem;

	public static List<DQMetricsValue> cacheValues;
	public static SystemLevelMetricsList totalSystemLevelMetricsList;
	public static int trendLength = 20 * 24;
	public static int trendOffset = 24 * 7;

	public void insertMetadata(DQMetricsValue dq) {

		List<KeyValue> queryList = new ArrayList<KeyValue>();
		queryList.add(new KeyValue("metricName", dq.getMetricName()));
		// queryList.add(new KeyValue("metricType", dq.getMetricType()));
		// queryList.add(new KeyValue("assetId", dq.getAssetId()));
		queryList.add(new KeyValue("timestamp", dq.getTimestamp()));

		List<KeyValue> updateValues = new ArrayList<KeyValue>();
		updateValues.add(new KeyValue("value", dq.getValue()));

		DBObject item = BarkMongoDAO.getModelDAO().getAllMetricsByCondition(
				queryList);

		if (item == null) {
			long seq = BarkMongoDAO.getModelDAO().getNextMetricsSequence();
			logger.warn("log: new record inserted" + seq);
			dq.set_id(seq);
			BarkMongoDAO.getModelDAO().saveDQMetricsValue(dq);
		} else {
			logger.warn("log: updated record");
			BarkMongoDAO.getModelDAO().updateDQMetricsValue(dq, item);
		}

	}

	public List<String> fetchAllAssetIdBySystems() {

		// List<String> assetIds = new ArrayList<String>();

		List<DQModelEntity> allModels = BarkMongoDAO.getModelDAO()
				.getAllModels();
		modelSystem = new HashMap<String, String>();

		for (DQModelEntity model : allModels) {
			// assetIds.add(asset.get("assetName").toString());
			modelSystem.put(model.getModelName(),
					SystemTypeConstants.val(model.getSystem()));
		}

		return null;

	}

	public DQMetricsValue getLatestlMetricsbyId(String assetId) {

		// Query<DQMetricsValue> q =
		// dqMetricsDao.getDatastore().createQuery(DQMetricsValue.class).filter("assetId",
		// assetId).order("-timestamp");
		// List<DQMetricsValue> v = (List<DQMetricsValue>) q.asList();
		//
		// if(v.isEmpty()){
		// return null;
		// }
		//
		// return v.get(0);
		return BarkMongoDAO.getModelDAO().getLatestMetricsByAssetId(assetId);
	}

	public void autoRefresh() {
		updateLatestDQList();
	}

	public void refreshAllDQMetricsValuesinCache() {
		fetchAllAssetIdBySystems();
		// cacheValues = dqMetricsDao.findByFieldInValues(DQMetricsValue.class,
		// "assetId", assetIds);

		cacheValues = new ArrayList<DQMetricsValue>();
		List<DBObject> allMetrics = BarkMongoDAO.getModelDAO().getAllMetrics();
		for (DBObject tempMetric : allMetrics) {
			cacheValues.add(new DQMetricsValue(tempMetric.get("metricName")
					.toString(), Long.parseLong(tempMetric.get("timestamp")
					.toString()), Float.parseFloat(tempMetric.get("value")
					.toString())));
		}
	}

	public void updateLatestDQList() {
		try {
			logger.warn("==============updating all latest dq metrics==================");
			// try {
			// Thread.sleep(30000);
			// } catch (InterruptedException e) {
			// e.printStackTrace();
			// }
			refreshAllDQMetricsValuesinCache();

			totalSystemLevelMetricsList = new SystemLevelMetricsList();
			for (DQMetricsValue temp : cacheValues) {
				// totalSystemLevelMetricsList.upsertNewAsset(temp, assetSystem,
				// 1);
				totalSystemLevelMetricsList.upsertNewAssetExecute(
						temp.getMetricName(), "", temp.getTimestamp(),
						temp.getValue(), modelSystem.get(temp.getMetricName()),
						0, 1, null);
			}

			totalSystemLevelMetricsList.updateDQFail(dqModelService
					.getThresholds());
			calculateReferenceMetrics();

			logger.warn("==============update all latest dq metrics done==================");
		} catch (Exception e) {
			logger.warn(e.toString());
			e.printStackTrace();
		}
	}

	public List<MADEntity> MAD(List<String> list) {
		List<MADEntity> result = new ArrayList<MADEntity>();
		int preparePointNumber = 15;
		float up_coff = (float) 2.3;
		float down_coff = (float) 2.3;
		for (int i = preparePointNumber; i < list.size(); i++) {
			long total = 0;
			for (int j = i - preparePointNumber; j < i; j++) {
				long rawNumber = Long.parseLong(list.get(j));
				total = total + rawNumber;
			}
			long mean = total / preparePointNumber;
			long meantotal = 0;
			for (int j = i - preparePointNumber; j < i; j++) {
				long rawNumber = Integer.parseInt(list.get(j));
				long rawDiff = rawNumber - mean;
				if (rawDiff >= 0)
					meantotal = meantotal + rawDiff;
				else
					meantotal = meantotal - rawDiff;
			}
			long mad = meantotal / preparePointNumber;
			long upper = (long) (mean + mad * up_coff);
			long lower = (long) (mean - mad * down_coff);
			// logger.warn( list.get(i)+"\t"+upper +"\t"+lower);
			result.add(new MADEntity(upper, lower));
		}
		logger.warn("mad done");
		return result;
	}

	public List<BollingerBandsEntity> bollingerBand(List<String> list) {
		List<BollingerBandsEntity> result = new ArrayList<BollingerBandsEntity>();
		int preparePointNumber = 30;
		float up_coff = (float) 1.8;
		float down_coff = (float) 1.8;
		for (int i = preparePointNumber; i < list.size(); i++) {
			long total = 0;
			for (int j = i - preparePointNumber; j < i; j++) {
				long rawNumber = Long.parseLong(list.get(j));
				total = total + rawNumber;
			}
			long mean = total / preparePointNumber;
			long meantotal = 0;
			for (int j = i - preparePointNumber; j < i; j++) {
				long rawNumber = Integer.parseInt(list.get(j));
				long rawDiff = rawNumber - mean;
				meantotal += rawDiff * rawDiff;
			}
			long mad = (long) Math.sqrt(meantotal / preparePointNumber);
			long upper = (long) (mean + mad * up_coff);
			long lower = (long) (mean - mad * down_coff);
			// .out.println( list.get(i)+"\t"+upper +"\t"+lower);
			result.add(new BollingerBandsEntity(upper, lower, mean));
		}
		logger.warn("bollingerband done");
		return result;
	}

	public void calculateReferenceMetrics() {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();

		HashMap<String, String> references = dqModelService.getReferences();
		Iterator iter = references.keySet().iterator();
		while (iter.hasNext()) {
			Object next = iter.next();
			String sourceName = next.toString();

			String referencerNames = references.get(next).toString();
			List<String> rNames = new ArrayList<String>();
			if (referencerNames.indexOf(",") == -1)
				rNames.add(referencerNames);
			else
				rNames = Arrays.asList(referencerNames.split(","));

			for (String referencerName : rNames) {
				logger.warn("==============anmoni loop start=================="
						+ referencerName + " " + sourceName);
				List<DQMetricsValue> sourceMetricValues = BarkMongoDAO
						.getModelDAO().getAllMetricsByMetricsName(sourceName);// dqMetricsDao.findByField(DQMetricsValue.class,
																				// "metricName",
																				// sourceName);
				DQModelEntity referencerModel = dqModelService
						.getGeneralModel(referencerName);
				if (referencerModel == null)
					continue;
				DQModelEntity sourceModel = dqModelService
						.getGeneralModel(sourceName);
				if (sourceModel == null)
					continue;

				if (sourceModel.getSchedule() == ScheduleTypeConstants.DAILY) {
					trendLength = 20;
					trendOffset = 7;
				} else {
					trendLength = 20 * 24;
					trendOffset = 7 * 24;
				}

				Collections.sort(sourceMetricValues);
				float threadshold = referencerModel.getThreshold();
				// type anomin detection trend
				if (referencerModel.getModelType() == ModelTypeConstants.ANOMALY_DETECTION) {
					String content = referencerModel.getModelContent();
					String[] contents = content.split("\\|");
					int type = Integer.parseInt(contents[2]);

					// History Trend Detection
					if (type == 1) {
						logger.warn("==============trend start=================="
								+ referencerName
								+ " "
								+ sourceName
								+ " "
								+ trendLength + " " + trendOffset);
						if (sourceMetricValues.size() <= trendLength
								+ trendOffset)
							continue;
						int dqfail = 0;
						if (sourceMetricValues.get(0).getValue()
								/ sourceMetricValues.get(trendOffset)
										.getValue() >= 1 + threadshold
								|| sourceMetricValues.get(0).getValue()
										/ sourceMetricValues.get(trendOffset)
												.getValue() <= 1 - threadshold)
							dqfail = 1;
						for (int i = 0; i <= trendLength; i++) {
							DQMetricsValue tempDQMetricsValue = sourceMetricValues
									.get(i);
							float lastValue = sourceMetricValues.get(
									i + trendOffset).getValue();
							totalSystemLevelMetricsList.upsertNewAssetExecute(
									referencerName,
									MetricType.Trend.toString(),
									tempDQMetricsValue.getTimestamp(),
									tempDQMetricsValue.getValue(), modelSystem
											.get(tempDQMetricsValue
													.getMetricName()), dqfail,
									1, new AssetLevelMetricsDetail(lastValue));
						}
						logger.warn("==============trend end==================");
					}
					// Bollinger Bands Detection
					else if (type == 2) {
						logger.warn("==============Bollinger start=================="
								+ referencerName + " " + sourceName);
						Collections.reverse(sourceMetricValues);
						List<String> sourceValues = new ArrayList<String>();
						for (int i = 0; i < sourceMetricValues.size(); i++) {
							sourceValues.add((long) sourceMetricValues.get(i)
									.getValue() + "");
						}

						List<BollingerBandsEntity> bollingers = bollingerBand(sourceValues);

						int dqfail = 0;
						if (sourceMetricValues.size() > 0) {
							if (sourceMetricValues.get(
									sourceMetricValues.size() - 1).getValue() < bollingers
									.get(bollingers.size() - 1).getLower()) {
								dqfail = 1;
							}

							int offset = sourceMetricValues.size()
									- bollingers.size();
							for (int i = offset; i < sourceMetricValues.size(); i++) {
								DQMetricsValue tempDQMetricsValue = sourceMetricValues
										.get(i);

								totalSystemLevelMetricsList
										.upsertNewAssetExecute(
												referencerName,
												MetricType.Bollinger.toString(),
												tempDQMetricsValue
														.getTimestamp(),
												tempDQMetricsValue.getValue(),
												modelSystem.get(tempDQMetricsValue
														.getMetricName()),
												dqfail,
												1,
												new AssetLevelMetricsDetail(
														new BollingerBandsEntity(
																bollingers
																		.get(i
																				- offset)
																		.getUpper(),
																bollingers
																		.get(i
																				- offset)
																		.getLower(),
																bollingers
																		.get(i
																				- offset)
																		.getMean())));
							}
						}
						logger.warn("==============Bollinger end=================="
								+ referencerName + " " + sourceName);
					}
					// MAD
					else if (type == 3) {
						logger.warn("==============MAD start=================="
								+ referencerName + " " + sourceName);
						Collections.reverse(sourceMetricValues);
						List<String> sourceValues = new ArrayList<String>();
						for (int i = 0; i < sourceMetricValues.size(); i++) {
							sourceValues.add((long) sourceMetricValues.get(i)
									.getValue() + "");
						}
						List<MADEntity> MADList = MAD(sourceValues);

						int dqfail = 0;
						if (sourceMetricValues.size() > 0) {
							if (sourceMetricValues.get(
									sourceMetricValues.size() - 1).getValue() < MADList
									.get(MADList.size() - 1).getLower())
								dqfail = 1;

							int offset = sourceMetricValues.size()
									- MADList.size();
							for (int i = offset; i < sourceMetricValues.size(); i++) {
								DQMetricsValue tempDQMetricsValue = sourceMetricValues
										.get(i);
								totalSystemLevelMetricsList
										.upsertNewAssetExecute(
												referencerName,
												MetricType.MAD.toString(),
												tempDQMetricsValue
														.getTimestamp(),
												tempDQMetricsValue.getValue(),
												modelSystem.get(tempDQMetricsValue
														.getMetricName()),
												dqfail,
												1,
												new AssetLevelMetricsDetail(
														new MADEntity(
																MADList.get(
																		i
																				- offset)
																		.getUpper(),
																MADList.get(
																		i
																				- offset)
																		.getLower())));
							}
						}
						logger.warn("==============MAD end==================");
					}
				}
				logger.warn("==============anmoni loop end=================="
						+ referencerName + " " + sourceName);
			}
		}
	}

	public List<SystemLevelMetrics> addAssetNames(
			List<SystemLevelMetrics> result) {
		List<ModelForFront> models = dqModelService.getAllModles();
		Map<String, String> modelMap = new HashMap<String, String>();

		for (ModelForFront model : models) {
			modelMap.put(
					model.getName(),
					model.getAssetName() == null ? "unknow" : model
							.getAssetName());
		}

		for (SystemLevelMetrics sys : result) {
			List<AssetLevelMetrics> assetList = sys.getMetrics();
			if (assetList != null && assetList.size() > 0) {
				for (AssetLevelMetrics metrics : assetList) {
					metrics.setAssetName(modelMap.get(metrics.getName()));
				}
			}
		}

		return result;
	}

	public Map<String, String> getAssetMap() {
		List<ModelForFront> models = dqModelService.getAllModles();
		Map<String, String> modelMap = new HashMap<String, String>();

		for (ModelForFront model : models) {
			modelMap.put(
					model.getName(),
					model.getAssetName() == null ? "unknow" : model
							.getAssetName());
		}

		return modelMap;
	}

	@Override
	public List<SystemLevelMetrics> briefMetrics(String system) {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();
		return totalSystemLevelMetricsList.getListWithLatestNAssets(24, system,
				null, null);
	}

	@Override
	public List<SystemLevelMetrics> heatMap() {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();
		return totalSystemLevelMetricsList.getHeatMap(dqModelService
				.getThresholds());
	}

	@Override
	public List<SystemLevelMetrics> dashboard(String system) {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();
		return addAssetNames(totalSystemLevelMetricsList
				.getListWithLatestNAssets(30, system, null, null));
	}

	@Override
	public List<SystemLevelMetrics> mydashboard(String user) {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();
		return addAssetNames(totalSystemLevelMetricsList
				.getListWithLatestNAssets(30, "all",
						subscribeService.getSubscribe(user), getAssetMap()));
	}

	@Override
	public AssetLevelMetrics oneDataCompleteDashboard(String name) {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();
		return totalSystemLevelMetricsList.getListWithSpecificAssetName(name);
	}

	@Override
	public AssetLevelMetrics oneDataBriefDashboard(String name) {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();
		return totalSystemLevelMetricsList.getListWithSpecificAssetName(name,
				30);
	}

	public OverViewStatistics getOverViewStats() {

		OverViewStatistics os = new OverViewStatistics();

		os.setAssets(BarkMongoDAO.getModelDAO().getAllAssets().size());
		os.setMetrics(BarkMongoDAO.getModelDAO().getAllModels().size());

		DQHealthStats health = new DQHealthStats();

		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();

		List<SystemLevelMetrics> allMetrics = totalSystemLevelMetricsList
				.getLatestDQList();

		int healthCnt = 0;
		int invalidCnt = 0;

		for (SystemLevelMetrics metricS : allMetrics) {

			List<AssetLevelMetrics> metricsA = metricS.getMetrics();

			for (AssetLevelMetrics m : metricsA) {
				if (m.getDqfail() == 0) {
					healthCnt++;
				} else {
					invalidCnt++;
				}
			}
		}

		health.setHealth(healthCnt);
		health.setInvalid(invalidCnt);

		health.setWarn(0);
		os.setStatus(health);

		return os;

	}

	@Override
	/**
	 * Get the metrics for 24 hours
	 */
	public AssetLevelMetrics metricsForReport(String name) {
		if (totalSystemLevelMetricsList == null)
			updateLatestDQList();
		return totalSystemLevelMetricsList.getListWithSpecificAssetName(name,
				24);
	}

	public List<SampleOut> listSampleFile(String modelName) {

		List<SampleOut> samples = new ArrayList<SampleOut>();

		List<DBObject> dbos = BarkMongoDAO.getModelDAO().findSampleByModelName(
				modelName);

		for (DBObject dbo : dbos) {

			SampleOut so = new SampleOut();

			so.setDate(Long.parseLong(dbo.get("timestamp").toString()));
			so.setPath(dbo.get("hdfsPath").toString());

			samples.add(so);
		}

		return samples;

	}

	public void insertSampleFilePath(SampleFilePathLKP samplePath) {
		SampleFilePathLKP entity = new SampleFilePathLKP();

		entity.set_id(BarkMongoDAO.getModelDAO().getNextFilePathSequence());
		entity.setModelName(samplePath.getModelName());
		entity.setTimestamp(samplePath.getTimestamp());
		entity.setHdfsPath(samplePath.getHdfsPath());

		BarkMongoDAO.getModelDAO().insertNewSamplePath(entity);

	}

	public void downloadSample(String filePath) {

	}

}
