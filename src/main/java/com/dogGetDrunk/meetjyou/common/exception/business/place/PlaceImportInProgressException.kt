package com.dogGetDrunk.meetjyou.common.exception.business.place

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException

class PlaceImportInProgressException : BusinessException(ErrorCode.PLACE_IMPORT_IN_PROGRESS, "place-import")
