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
package org.apache.bark.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.apache.bark.common.MetricType;
import org.apache.bark.service.DQModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@XmlRootElement
public class SystemLevelMetricsList {
	private static Logger logger = LoggerFactory.getLogger(SystemLevelMetricsList.class);

	public List<SystemLevelMetrics> latestDQList;

	public String limitPlatform = "Apollo";

	@Autowired
	private DQModelService dqModelService;

	public SystemLevelMetricsList()
	{
		latestDQList = new ArrayList<SystemLevelMetrics>();
	}

	public int containsAsset(String system, String name)
	{
		for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
		{
			if(tempSystemLevelMetrics.getName().equals(system))
			{
				for(AssetLevelMetrics tempAssetLevelMetrics : tempSystemLevelMetrics.getMetrics())
				{
					if(tempAssetLevelMetrics.getName().equals(name))
					{
						return 1;
					}
				}
			}
		}
		return 0;
	}

	public SystemLevelMetrics getSystemLevelMetrics(String system)
	{
		for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
		{
			if(tempSystemLevelMetrics.getName().equals(system))
			{
				return tempSystemLevelMetrics;
			}
		}
		return null;
	}


	public List<SystemLevelMetrics> getListWithLatestNAssets(int N, String system, UserSubscribeItem subscribe, Map<String, String> assetMap)
	{

		List<SystemLevelMetrics> result = new ArrayList<SystemLevelMetrics>();
		try{

			for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
			{
				if(tempSystemLevelMetrics.getName().equals(system) || system.equals("all"))
				{
					SystemLevelMetrics tempSystemLevelMetrics1 = new SystemLevelMetrics(tempSystemLevelMetrics.getName());
					tempSystemLevelMetrics1.setDq(tempSystemLevelMetrics.getDq());
					List<AssetLevelMetrics> metrics = tempSystemLevelMetrics.getMetrics();

					boolean isCurrentSystemSelected;
					if(subscribe != null) isCurrentSystemSelected = subscribe.isSystemSelected(limitPlatform, tempSystemLevelMetrics.getName());
					else isCurrentSystemSelected = true;

					for(AssetLevelMetrics tempAssetLevelMetrics : metrics)
					{
						boolean isCurrentDataassetSelected;
						if(subscribe != null) isCurrentDataassetSelected = subscribe.isDataAssetSelected(limitPlatform, tempSystemLevelMetrics.getName(), assetMap.get(tempAssetLevelMetrics.getName()));
						else isCurrentDataassetSelected = true;

						if(isCurrentSystemSelected || isCurrentDataassetSelected)
						{
							AssetLevelMetrics tempAssetLevelMetrics1 = new AssetLevelMetrics(tempAssetLevelMetrics.getName(),tempAssetLevelMetrics.getMetricType(),tempAssetLevelMetrics.getDq(),tempAssetLevelMetrics.getTimestamp(),tempAssetLevelMetrics.getDqfail());
							List<AssetLevelMetricsDetail> otherdetails = tempAssetLevelMetrics.getDetails();
							if(otherdetails!=null)
							{
								Collections.sort(otherdetails);
							}
							List<AssetLevelMetricsDetail> tempdetails = new ArrayList<AssetLevelMetricsDetail>();

							for(int i=0;i<otherdetails.size() && i<N;i++)
							{
								tempdetails.add(otherdetails.get(i));
							}
							tempAssetLevelMetrics1.setDetails(tempdetails);

							tempSystemLevelMetrics1.addAssetLevelMetrics(tempAssetLevelMetrics1);
						}
					}

					if(tempSystemLevelMetrics1.getMetrics().size()>0) result.add(tempSystemLevelMetrics1);
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return result;
	}

	public AssetLevelMetrics getListWithSpecificAssetName(String name)
	{
		List<SystemLevelMetrics> result = new ArrayList<SystemLevelMetrics>();
		for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
		{
			SystemLevelMetrics tempSystemLevelMetrics1 = new SystemLevelMetrics(tempSystemLevelMetrics.getName());
			tempSystemLevelMetrics1.setDq(tempSystemLevelMetrics.getDq());
			for(AssetLevelMetrics tempAssetLevelMetrics : tempSystemLevelMetrics.getMetrics())
			{
				if(tempAssetLevelMetrics.getName().equals(name))
				{
					return tempAssetLevelMetrics;
				}
			}
		}

		return null;
	}

	public AssetLevelMetrics getListWithSpecificAssetName(String name, int count)
	{
		List<SystemLevelMetrics> result = new ArrayList<SystemLevelMetrics>();
		for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
		{
			SystemLevelMetrics tempSystemLevelMetrics1 = new SystemLevelMetrics(tempSystemLevelMetrics.getName());
			tempSystemLevelMetrics1.setDq(tempSystemLevelMetrics.getDq());
			for(AssetLevelMetrics tempAssetLevelMetrics : tempSystemLevelMetrics.getMetrics())
			{
				if(tempAssetLevelMetrics.getName().equals(name))
				{
					return new AssetLevelMetrics(tempAssetLevelMetrics,count);
				}
			}
		}

		return null;
	}

	public void updateDQFail(HashMap<String, String> thresholds)
	{
		for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
		{
			for(AssetLevelMetrics tempAssetLevelMetrics : tempSystemLevelMetrics.getMetrics())
			{
				if(thresholds.containsKey(tempAssetLevelMetrics.getName()))
				{
					if(tempAssetLevelMetrics.getDq() < Float.parseFloat(thresholds.get(tempAssetLevelMetrics.getName())))
					{
						tempAssetLevelMetrics.setDqfail(1);
					}
				}
			}
		}
	}


	public List<SystemLevelMetrics> getHeatMap(HashMap<String, String> thresholds)
	{
		List<SystemLevelMetrics> result = new ArrayList<SystemLevelMetrics>();

		SystemLevelMetricsList latestSystemLevelMetricsList = new SystemLevelMetricsList();
		latestSystemLevelMetricsList.setLatestDQList(latestDQList);
		SystemLevelMetricsList resultSystemLevelMetricsList = new SystemLevelMetricsList();

		for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
		{
			int size = 0;
			for(AssetLevelMetrics tempAssetLevelMetrics : tempSystemLevelMetrics.getMetrics())
			{
				if(thresholds.containsKey(tempAssetLevelMetrics.getName()))
				{
					if(tempAssetLevelMetrics.getDq() < Float.parseFloat(thresholds.get(tempAssetLevelMetrics.getName())))
					{
						tempAssetLevelMetrics.setDqfail(1);
						resultSystemLevelMetricsList.upsertNewAssetExecute(
								tempAssetLevelMetrics.getName(),
								tempAssetLevelMetrics.getMetricType(),
								tempAssetLevelMetrics.getTimestamp(),
								tempAssetLevelMetrics.getDq(),
								tempSystemLevelMetrics.getName(),
								tempAssetLevelMetrics.getDqfail(), 0, null);

						size++;
					}
				}
			}
			if(size==0) resultSystemLevelMetricsList.getLatestDQList().add(new SystemLevelMetrics(tempSystemLevelMetrics.getName()));
		}

		result = resultSystemLevelMetricsList.getLatestDQList();
		for(SystemLevelMetrics tempSystemLevelMetrics : result)
		{
			int size = tempSystemLevelMetrics.getMetrics().size();
			String system = tempSystemLevelMetrics.getName();
			if(size < 8)
			{
				SystemLevelMetrics currentLatestSystemLevelMetrics = latestSystemLevelMetricsList.getSystemLevelMetrics(tempSystemLevelMetrics.getName());
				for(AssetLevelMetrics currentLatestAssetLevelMetrics : currentLatestSystemLevelMetrics.getMetrics())
				{
					if(resultSystemLevelMetricsList.containsAsset(system, currentLatestAssetLevelMetrics.getName())==0)
					{
						//resultSystemLevelMetricsList.upsertNewAsset(currentLatestAssetLevelMetrics, system, assetSystem, 0);
						resultSystemLevelMetricsList.upsertNewAssetExecute(
								currentLatestAssetLevelMetrics.getName(),
								currentLatestAssetLevelMetrics.getMetricType(),
								currentLatestAssetLevelMetrics.getTimestamp(),
								currentLatestAssetLevelMetrics.getDq(),
								system,
								currentLatestAssetLevelMetrics.getDqfail(),
								0, null);
						size++;
						if(size>=8) break;
					}
				}
			}
		}

		return result;
	}

	public void upsertNewAssetExecute(String metricName, String metricType, long timestamp, float dq, String currentSystem, int dqfail, int needdetail, AssetLevelMetricsDetail otherAttributes)
	{
		int systemIndicator = 0;
		if(currentSystem==null) currentSystem="unknown";
		try{
			for(SystemLevelMetrics tempSystemLevelMetrics : latestDQList)
			{
				//find the system item
				if(tempSystemLevelMetrics.getName().equals(currentSystem))
				{
					List<AssetLevelMetrics> tempassetLevelMetricsArray = tempSystemLevelMetrics.getMetrics();
					int metricIndicator = 0;
					for(int k=0;k<tempassetLevelMetricsArray.size();k++)
					{
						AssetLevelMetrics tempAssetLevelMetrics = tempassetLevelMetricsArray.get(k);
						//find the metric
						if(tempAssetLevelMetrics.getName().equals(metricName))
						{
							if(tempAssetLevelMetrics.getTimestamp()-(timestamp)<0)
							{
								tempAssetLevelMetrics.setTimestamp(timestamp);
								tempAssetLevelMetrics.setDq(dq);
								tempAssetLevelMetrics.setDqfail(dqfail);
								tempassetLevelMetricsArray.set(k, tempAssetLevelMetrics);
							}
							metricIndicator = 1;
							if(needdetail==1)
							{
								if(metricType.equals(MetricType.Bollinger.toString())) tempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq, new BollingerBandsEntity(otherAttributes.getBolling().getUpper(),otherAttributes.getBolling().getLower(),otherAttributes.getBolling().getMean())));
								else if(metricType.equals(MetricType.Trend.toString())) tempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq, otherAttributes.getComparisionValue()));
								else if(metricType.equals(MetricType.MAD.toString())) tempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq, otherAttributes.getMAD()));
								else tempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq));
							}
						}
					}
					//didn't find the metric, create one
					if(metricIndicator == 0)
					{
						AssetLevelMetrics newTempAssetLevelMetrics = new AssetLevelMetrics(metricName, metricType, dq, timestamp, dqfail);
						if(needdetail==1)
						{
							if(metricType.equals(MetricType.Bollinger.toString())) newTempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq, new BollingerBandsEntity(otherAttributes.getBolling().getUpper(),otherAttributes.getBolling().getLower(),otherAttributes.getBolling().getMean())));
							else if(metricType.equals(MetricType.Trend.toString())) newTempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq, otherAttributes.getComparisionValue()));
							else if(metricType.equals(MetricType.MAD.toString())) newTempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq, otherAttributes.getMAD()));
							else newTempAssetLevelMetrics.addAssetLevelMetricsDetail(new AssetLevelMetricsDetail(timestamp, dq));
						}
						tempassetLevelMetricsArray.add(newTempAssetLevelMetrics);
					}
					systemIndicator = 1;
				}
			}
		}catch(Exception e)
		{
			logger.warn(e.toString());
			e.printStackTrace();
		}

		//can't find the system
		if(systemIndicator == 0)
		{
			SystemLevelMetrics newSystemLevelMetrics = new SystemLevelMetrics(currentSystem);
			newSystemLevelMetrics.addAssetLevelMetrics(new AssetLevelMetrics(metricName, metricType, dq, timestamp, dqfail));
			if(needdetail==1)
			{
				List<AssetLevelMetricsDetail> tempDetailList = new ArrayList<AssetLevelMetricsDetail>();
				tempDetailList.add(new AssetLevelMetricsDetail(timestamp,dq));
				newSystemLevelMetrics.getMetrics().get(0).setDetails(tempDetailList);
			}
			latestDQList.add(newSystemLevelMetrics);
		}
	}


	public List<SystemLevelMetrics> getLatestDQList() {
		return latestDQList;
	}

	public void setLatestDQList(List<SystemLevelMetrics> latestDQList) {
		this.latestDQList = latestDQList;
	}


}
