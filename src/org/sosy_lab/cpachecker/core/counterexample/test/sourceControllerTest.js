// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2018 Lokesh Nandanwar
// SPDX-FileCopyrightText: 2018-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

describe("ReportController", function () {
  let $rootScope;
  let $scope;
  let controller;

  beforeEach(function () {
    angular.mock.module("report");

    angular.mock.inject(function ($injector) {
      $rootScope = $injector.get("$rootScope");
      $scope = $rootScope.$new();
      controller = $injector.get("$controller")("SourceController", {
        $scope: $scope,
      });
    });
    jasmine.getFixtures().fixturesPath = "base/";
    jasmine.getFixtures().load("testReport.html");
  });

  describe("sourceFiles initialization", function () {
    it("Should be defined", function () {
      expect($scope.sourceFiles).not.toBeUndefined();
    });
  });

  describe("selectedSourceFile initialization", function () {
    it("Should be defined", function () {
      expect($scope.selectedSourceFile).not.toBeUndefined();
    });
  });

  describe("setSourceFile action handler", function () {
    it("Should be defined", function () {
      expect($scope.setSourceFile).not.toBeUndefined();
    });
  });

  describe("sourceFileIsSet action handler", function () {
    it("Should be defined", function () {
      expect($scope.sourceFileIsSet).not.toBeUndefined();
    });
  });
});
