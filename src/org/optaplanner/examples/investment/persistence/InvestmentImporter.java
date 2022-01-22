/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.examples.investment.persistence;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.optaplanner.examples.common.persistence.AbstractXlsxSolutionImporter;
import org.optaplanner.examples.common.persistence.SolutionConverter;
import org.optaplanner.examples.investment.app.InvestmentApp;
import org.optaplanner.examples.investment.domain.AssetClass;
import org.optaplanner.examples.investment.domain.AssetClassAllocation;
import org.optaplanner.examples.investment.domain.InvestmentParametrization;
import org.optaplanner.examples.investment.domain.InvestmentSolution;
import org.optaplanner.examples.investment.domain.Region;
import org.optaplanner.examples.investment.domain.Sector;
import org.optaplanner.examples.investment.domain.util.InvestmentNumericUtil;

public class InvestmentImporter extends AbstractXlsxSolutionImporter<InvestmentSolution> {

	public static void main(String[] args) {
		SolutionConverter<InvestmentSolution> converter = SolutionConverter
				.createImportConverter(InvestmentApp.DATA_DIR_NAME, new InvestmentImporter(), InvestmentSolution.class);
		converter.convert("de_smet_1.xlsx", "de_smet_1.xml");
	}

	@Override
	public XlsxInputBuilder<InvestmentSolution> createXlsxInputBuilder() {
		return new InvestmentAllocationInputBuilder();
	}

	public static class InvestmentAllocationInputBuilder extends XlsxInputBuilder<InvestmentSolution> {

		private InvestmentSolution solution;

		private final Map<String, Region> regionMap = new HashMap<>();
		private final Map<String, Sector> sectorMap = new HashMap<>();

		@Override
		public InvestmentSolution readSolution() throws IOException {
			solution = new InvestmentSolution();
			solution.setId(0L);
			readParametrization();
			readRegionList();
			readSectorList();
			readAssetClassList();
			createAssetClassAllocationList();

			BigInteger possibleSolutionSize = BigInteger.valueOf(solution.getAssetClassList().size())
					.multiply(BigInteger.valueOf(InvestmentNumericUtil.MAXIMUM_QUANTITY_MILLIS));
			logger.info(
					"InvestmentAllocation {} has {} regions, {} sectors and {} asset classes"
							+ " with a search space of {}.",
					getInputId(), solution.getRegionList().size(), solution.getSectorList().size(),
					solution.getAssetClassList().size(), getFlooredPossibleSolutionSize(possibleSolutionSize));
			return solution;
		}

		private void readParametrization() throws IOException {
			InvestmentParametrization parametrization = new InvestmentParametrization();
			parametrization.setId(0L);
			parametrization.setStandardDeviationMillisMaximum(73);
			solution.setParametrization(parametrization);
		}

		private void readRegionList() throws IOException {
			List<Region> regionList = new ArrayList<>();
			{
				var region = new Region();
				region.setId(0L);
				region.setName("Global");
				region.setQuantityMillisMaximum(1000L);
				regionList.add(region);
			}
			for (var region : regionList) {
				this.regionMap.put(region.getName(), region);
			}
			solution.setRegionList(regionList);
		}

		private void readSectorList() throws IOException {
			Sheet sheet = readSheet(2, "Sectors");
			Row headerRow = sheet.getRow(0);
			assertCellConstant(headerRow.getCell(0), "Name");
			assertCellConstant(headerRow.getCell(1), "Quantity maximum");
			List<Sector> sectorList = new ArrayList<>();
			{
				var sector = new Sector();
				sector.setId(0L);
				sector.setName("Tech");
				sector.setQuantityMillisMaximum(300L);
				sectorList.add(sector);
			}
			{
				var sector = new Sector();
				sector.setId(1L);
				sector.setName("Cars");
				sector.setQuantityMillisMaximum(1000L);
				sectorList.add(sector);
			}
			{
				var sector = new Sector();
				sector.setId(2L);
				sector.setName("Food");
				sector.setQuantityMillisMaximum(40L);
				sectorList.add(sector);
			}
			for (var sector : sectorList) {
				this.sectorMap.put(sector.getName(), sector);
			}
			solution.setSectorList(sectorList);
		}

		private void readAssetClassList() throws IOException {
			Map<Long, AssetClass> assets = new HashMap<>();
			{
				var asset = new AssetClass();
				asset.setId(1L);
				asset.setName("Red Hat, Inc.");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Tech"));
				asset.setExpectedReturnMillis(1365);
				asset.setStandardDeviationRiskMillis(2913);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(2L);
				asset.setName("Google Inc.");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Tech"));
				asset.setExpectedReturnMillis(1567);
				asset.setStandardDeviationRiskMillis(2158);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(3L);
				asset.setName("Oracle Corporation");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Tech"));
				asset.setExpectedReturnMillis(1232);
				asset.setStandardDeviationRiskMillis(2171);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(4L);
				asset.setName("Apple Inc.");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Tech"));
				asset.setExpectedReturnMillis(2884);
				asset.setStandardDeviationRiskMillis(2414);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(5L);
				asset.setName("Microsoft Corporation");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Tech"));
				asset.setExpectedReturnMillis(1798);
				asset.setStandardDeviationRiskMillis(2070);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(6L);
				asset.setName("Tesla Motors, Inc.");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Cars"));
				asset.setExpectedReturnMillis(5473);
				asset.setStandardDeviationRiskMillis(5398);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(7L);
				asset.setName("Ford Motor Company");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Cars"));
				asset.setExpectedReturnMillis(105);
				asset.setStandardDeviationRiskMillis(2592);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(8L);
				asset.setName("Toyota Motor Corp Ltd Ord");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Tech"));
				asset.setExpectedReturnMillis(1363);
				asset.setStandardDeviationRiskMillis(1920);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(9L);
				asset.setName("General Motors Company");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Tech"));
				asset.setExpectedReturnMillis(210);
				asset.setStandardDeviationRiskMillis(2959);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(10L);
				asset.setName("Starbucks Corporation");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Food"));
				asset.setExpectedReturnMillis(3321);
				asset.setStandardDeviationRiskMillis(1974);
				assets.put(asset.getId(), asset);
			}
			{
				var asset = new AssetClass();
				asset.setId(11L);
				asset.setName("McDonald's Corporation");
				asset.setRegion(this.regionMap.get("Global"));
				asset.setSector(this.sectorMap.get("Food"));
				asset.setExpectedReturnMillis(805);
				asset.setStandardDeviationRiskMillis(1134);
				assets.put(asset.getId(), asset);
			}

			{
				var asset = assets.get(1L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(asset, 0L);
				map.put(assets.get(2L), 5L);
				map.put(assets.get(3L), 6L);
				map.put(assets.get(4L), 13L);
				map.put(assets.get(5L), 14L);
				map.put(assets.get(6L), 23L);
				map.put(assets.get(7L), 21L);
				map.put(assets.get(8L), 8L);
				map.put(assets.get(9L), 32L);
				map.put(assets.get(10L), 33L);
				map.put(assets.get(11L), 0L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(2L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 5L);
				map.put(asset, 0L);
				map.put(assets.get(3L), 5L);
				map.put(assets.get(4L), 26L);
				map.put(assets.get(5L), 18L);
				map.put(assets.get(6L), 10L);
				map.put(assets.get(7L), 8L);
				map.put(assets.get(8L), 20L);
				map.put(assets.get(9L), 21L);
				map.put(assets.get(10L), 21L);
				map.put(assets.get(11L), 21L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(3L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 60L);
				map.put(assets.get(2L), 5L);
				map.put(asset, 0L);
				map.put(assets.get(4L), 19L);
				map.put(assets.get(5L), 33L);
				map.put(assets.get(6L), 14L);
				map.put(assets.get(7L), 42L);
				map.put(assets.get(8L), 19L);
				map.put(assets.get(9L), 50L);
				map.put(assets.get(10L), 17L);
				map.put(assets.get(11L), -1L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(4L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 13L);
				map.put(assets.get(2L), 26L);
				map.put(assets.get(3L), 19L);
				map.put(asset, 0L);
				map.put(assets.get(5L), 27L);
				map.put(assets.get(6L), 1L);
				map.put(assets.get(7L), 15L);
				map.put(assets.get(8L), 18L);
				map.put(assets.get(9L), 25L);
				map.put(assets.get(10L), 23L);
				map.put(assets.get(11L), 3L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(5L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 14L);
				map.put(assets.get(2L), 18L);
				map.put(assets.get(3L), 33L);
				map.put(assets.get(4L), 27L);
				map.put(asset, 0L);
				map.put(assets.get(6L), 18L);
				map.put(assets.get(7L), 29L);
				map.put(assets.get(8L), 25L);
				map.put(assets.get(9L), 31L);
				map.put(assets.get(10L), 17L);
				map.put(assets.get(11L), 16L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(6L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 23L);
				map.put(assets.get(2L), 10L);
				map.put(assets.get(3L), 14L);
				map.put(assets.get(4L), 1L);
				map.put(assets.get(5L), 18L);
				map.put(asset, 0L);
				map.put(assets.get(7L), 32L);
				map.put(assets.get(8L), 16L);
				map.put(assets.get(9L), 23L);
				map.put(assets.get(10L), 24L);
				map.put(assets.get(11L), -5L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(7L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 21L);
				map.put(assets.get(2L), 8L);
				map.put(assets.get(3L), 42L);
				map.put(assets.get(4L), 15L);
				map.put(assets.get(5L), 29L);
				map.put(assets.get(6L), 32L);
				map.put(asset, 0L);
				map.put(assets.get(8L), 24L);
				map.put(assets.get(9L), 83L);
				map.put(assets.get(10L), 36L);
				map.put(assets.get(11L), 10L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(8L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 8L);
				map.put(assets.get(2L), 20L);
				map.put(assets.get(3L), 19L);
				map.put(assets.get(4L), 18L);
				map.put(assets.get(5L), 25L);
				map.put(assets.get(6L), 16L);
				map.put(assets.get(7L), 24L);
				map.put(asset, 0L);
				map.put(assets.get(9L), 36L);
				map.put(assets.get(10L), 32L);
				map.put(assets.get(11L), 10L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(9L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 32L);
				map.put(assets.get(2L), 21L);
				map.put(assets.get(3L), 50L);
				map.put(assets.get(4L), 25L);
				map.put(assets.get(5L), 32L);
				map.put(assets.get(6L), 23L);
				map.put(assets.get(7L), 83L);
				map.put(assets.get(8L), 36L);
				map.put(asset, 0L);
				map.put(assets.get(10L), 30L);
				map.put(assets.get(11L), 9L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(10L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 33L);
				map.put(assets.get(2L), 21L);
				map.put(assets.get(3L), 17L);
				map.put(assets.get(4L), 23L);
				map.put(assets.get(5L), 17L);
				map.put(assets.get(6L), 24L);
				map.put(assets.get(7L), 36L);
				map.put(assets.get(8L), 32L);
				map.put(assets.get(9L), 30L);
				map.put(asset, 0L);
				map.put(assets.get(11L), 31L);
				asset.setCorrelationMillisMap(map);
			}
			{
				var asset = assets.get(11L);
				Map<AssetClass, Long> map = new HashMap<>();
				map.put(assets.get(1L), 0L);
				map.put(assets.get(2L), 21L);
				map.put(assets.get(3L), -1L);
				map.put(assets.get(4L), 3L);
				map.put(assets.get(5L), 16L);
				map.put(assets.get(6L), -5L);
				map.put(assets.get(7L), 10L);
				map.put(assets.get(8L), 10L);
				map.put(assets.get(9L), 9L);
				map.put(assets.get(10L), 31L);
				map.put(asset, 0L);
				asset.setCorrelationMillisMap(map);
			}
			solution.setAssetClassList(new ArrayList<>(assets.values()));
		}

		private void createAssetClassAllocationList() {
			List<AssetClass> assetClassList = solution.getAssetClassList();
			List<AssetClassAllocation> assetClassAllocationList = new ArrayList<>(assetClassList.size());
			for (AssetClass assetClass : assetClassList) {
				AssetClassAllocation allocation = new AssetClassAllocation();
				allocation.setId(assetClass.getId());
				allocation.setAssetClass(assetClass);
				assetClassAllocationList.add(allocation);
			}
			solution.setAssetClassAllocationList(assetClassAllocationList);
		}

	}

}
