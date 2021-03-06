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

import java.util.List;

import org.apache.bark.common.BarkDbOperationException;
import org.apache.bark.model.DataAsset;
import org.apache.bark.model.DataAssetInput;
import org.apache.bark.model.PlatformMetadata;

public interface DataAssetService {
	public List<DataAsset> getAllDataAssets();
	public int createDataAsset(DataAssetInput input) throws BarkDbOperationException;
	public int updateDataAsset(DataAssetInput input) throws BarkDbOperationException;
	public DataAsset getDataAssetById(Long id) throws BarkDbOperationException;
	public List<PlatformMetadata> getSourceTree();
	public int removeAssetById(Long id) throws BarkDbOperationException;
}
