//
// Instantiated for each type in the CAL code with TYPE defined to it
//

#define ATYPE_(type) __array4##type
#define ATYPE__(type) ATYPE_(type)
#define ATYPE ATYPE__(TYPE)
#define FTYPE_(name, type) name##type
#define FTYPE__(name, type) FTYPE_(name,type)
#define FTYPE(name) FTYPE__(name,TYPE)

/*
 * Flags (true/false):
 * 0:01 direct(p)/indirect(pp)
 * 1:02 allocated(sz correct)/not-allocated
 * 2:04 on heap/on stack
 * 3:08 codegen/cal variable
 * 4:10 part of multi-dim/the full array
 */

#ifdef TYPE_DIRECT

__attribute__((always_inline)) static inline int FTYPE(free)(ATYPE *src, int top) {
    if ((src->flags & 0x17) == 0x7 && src->p != NULL) {
        free(src->p);
    }
    return TRUE;
}

__attribute__((always_inline)) static inline int FTYPE(copyEachArray)(TYPE *array, ATYPE *src, __arrayArg size) {
    //printf("    Copy each\n");
    switch (size.len) {
        case 1:
            memcpy(array, src->p, sizeof(TYPE) * src->sz[0]);
            break;
        case 2:
            for (int i0 = 0; i0 < src->sz[0]; i0++) {
                memcpy(array + size.sz[1] * i0, src->p + src->sz[1] * i0, sizeof(TYPE) * src->sz[1]);
            }
            break;
        case 3:
            for (int i1 = 0; i1 < src->sz[1]; i1++) {
                for (int i0 = 0; i0 < src->sz[0]; i0++) {
                    memcpy(array + (size.sz[1] * i0 + i1) * size.sz[2], src->p + (src->sz[1] * i0 + i1) * src->sz[2],
                           sizeof(TYPE) * src->sz[2]);
                }
            }
            break;
        case 4:
            for (int i2 = 0; i2 < src->sz[2]; i2++) {
                for (int i1 = 0; i1 < src->sz[1]; i1++) {
                    for (int i0 = 0; i0 < src->sz[0]; i0++) {
                        memcpy(array + ((size.sz[1] * i0 + i1) * size.sz[2] + i2) * size.sz[3],
                               src->p + ((src->sz[1] * i0 + i1) * src->sz[2] + i2) * src->sz[3],
                               sizeof(TYPE) * src->sz[3]);
                    }
                }
            }
            break;
    }
    return TRUE;
}

__attribute__((always_inline)) static inline char *FTYPE(serializeEach)(ATYPE *src, char *array) {
    *(int32_t *) array = src->dim;
    array += sizeof(int32_t);
    memcpy(array, src->sz, sizeof(int32_t) * 4);
    array += sizeof(int32_t) * 4;
    int tot = 1;
    for (int i = 0; i < src->dim; i++) {
        tot *= src->sz[i];
    }
    memcpy(array, src->p, sizeof(TYPE) * tot);
    array += sizeof(TYPE) * tot;
    return array;
}

__attribute__((always_inline)) static inline char *FTYPE(deserializeEach)(ATYPE *dst, char *array) {
    int32_t dim = *array;
    array += sizeof(int32_t);
    int32_t *sz = (int32_t *) array;
    array += sizeof(int32_t) * 4;
    int tot = 1;
    for (int i = 0; i < dim; i++) {
        tot *= sz[i];
        dst->sz[i] = sz[i];
    }
    dst->p = calloc(sizeof(TYPE), tot);
    dst->flags = 0x7;
    dst->dim = dim;
    memcpy(dst->p, array, sizeof(TYPE) * tot);
    array += sizeof(TYPE) * tot;
    return array;
}

__attribute__((always_inline)) static inline long FTYPE(sizeEach)(ATYPE *src) {
    long tot = 1;
    for (int i = 0; i < src->dim; i++) {
        tot *= src->sz[i];
    }
    return sizeof(TYPE) * tot + sizeof(int32_t) * 5;
}

__attribute__((always_inline)) static inline int FTYPE(reallocMoveArray)(ATYPE *dst, ATYPE *src, __arrayArg size) {
    int noCopy = TRUE;
    TYPE *array;
    if ((dst->flags & 0x3) != 0x3) {
        //Not allocated just alloc
        //printf("  Dst not allocated, just allocate\n");
        int sz = 1;
        for (int i = 0; i < size.len; i++) {
            sz *= size.sz[i];
            dst->sz[i] = size.sz[i];
        }
        dst->p = calloc(sizeof(TYPE), sz);
        dst->flags = 0x7 | dst->flags;
        dst->dim = size.len;
        if (src != NULL) {
            //Need to move data from src to dst
            noCopy = FALSE;
            array = dst->p;
        }
    } else {
        if (dst->dim != size.len) {
            //Ooops not same dimensions, something is wrong
            //Make it crash, since the generated code will not have bugs and it must be an external function using this in the wrong way
            //printf("  Ooops not same dimensions, something is wrong\n");
            dst->p = NULL;
            return FALSE;
        }
        //check if need more memory
        //printf("  Check if need more memory\n");
        int szMax = 1;
        for (int i = 0; i < size.len; i++) {
            szMax *= size.sz[i];
            if (size.sz[i] > dst->sz[i]) {
                noCopy = FALSE;
            }
        }
        if (!noCopy) {
            //printf("  allocate memory\n");
            array = calloc(sizeof(TYPE), szMax);
        }
        if (src != NULL) {
            //Move data from src to dst instead of from dst to new dst
            noCopy = FALSE;
        } else {
            src = dst;
        }
    }
    if (!noCopy) {
        //Need to copy each old value list seperatly
        //printf("  Need to move each list seperately\n");
        FTYPE(copyEachArray)(array, src, size);
        if ((dst->flags & 0x7) == 0x7 && dst->p != array) {
            free(dst->p);
        }
        dst->p = array;
        dst->flags |= 0x7;
        memcpy(dst->sz, size.sz, sizeof(int32_t) * 4);
    }
    return TRUE;
}

__attribute__((always_inline)) static inline int
FTYPE(copy)(ATYPE *dst, ATYPE *src, __arrayArg dstIndex, __arrayArg srcIndex, __arrayArg srcMax) {
    if (dstIndex.len == 0 && srcIndex.len == 0 && (dst->dim == src->dim || dst->dim == 0)) {
        //assign full variable dst from full variable src
        //we can just change ref when dst is full array & src allocated on heap and is a codegen var and not part of multi-dim.
        if ((dst->flags & 0x10) == 0x0 && (src->flags & 0x1f) == 0xf) {
            //printf("Just move ref\n");
            //free old dst and redirect
            if ((dst->flags & 0x7) == 0x7) {
                free(dst->p);
            }
            dst->p = src->p;
            memcpy(dst->sz, src->sz, sizeof(int32_t) * 4);
            dst->flags = (src->flags & 0x7) | (dst->flags & 0x8);
            src->flags = 0x1;
        } else {
            //need to copy data
            //printf("Copy data\n");
            FTYPE(reallocMoveArray)(dst, src, srcMax);
            //When src is codegen var on heap and not a part of multi-dim free it
            if ((src->flags & 0x1f) == 0xf) {
                free(src->p);
                src->flags = 0x1;
            }
        }
    } else {
        //partial array copying
        //printf("Partial array copy\n");
        if ((dst->dim - dstIndex.len) != (src->dim - srcIndex.len)) {
            //Error not same dimensions
            //printf("Not same dimensions!!!\n");
            return FALSE;
        }
        //Make sure dst is big enough
        //printf("Make sure dst is big enough\n");
        FTYPE(reallocMoveArray)(dst, NULL, srcMax);
        //Create temporary partial arrays
        //printf("Create temp partial arrays\n");
        ATYPE tmpSrc;
        int index = 0;
        int i;
        for (i = 0; i < dstIndex.len; i++) {
            index += dstIndex.sz[i];
            index *= srcMax.sz[i + 1];
        }
        for (; i < srcMax.len - 1; i++) {
            index *= srcMax.sz[i + 1];
        }
        TYPE *array = dst->p + index;
        __arrayArg partialSize;
        for (i = 0; i < dst->dim - dstIndex.len; i++) {
            partialSize.sz[i] = srcMax.sz[dstIndex.len + i];
        }
        partialSize.len = dst->dim - dstIndex.len;
        index = 0;
        for (i = 0; i < srcIndex.len; i++) {
            index += srcIndex.sz[i];
            index *= src->sz[i + 1];
        }
        for (; i < src->dim - 1; i++) {
            index *= src->sz[i + 1];
        }
        tmpSrc.p = src->p + index;
        for (i = 0; i < src->dim - srcIndex.len; i++) {
            tmpSrc.sz[i] = src->sz[srcIndex.len + i];
        }
        tmpSrc.dim = src->dim - srcIndex.len;
        tmpSrc.flags = 0x17;
        //and copy it
        //printf("Copy each array\n");
        FTYPE(copyEachArray)(array, &tmpSrc, partialSize);
        //When src is codegen var on heap and not a part of multi-dim free it
        if ((src->flags & 0x1f) == 0xf) {
            //printf("Free old codegen\n");
            free(src->p);
            src->flags = 0x1;
        }
    }
    //printf("copied dst sz[%i]:%i, %i, %i, %i\n",dst->dim,dst->sz[0],dst->sz[1],dst->sz[2],dst->sz[3]);
    return TRUE;
}

#else
__attribute__((always_inline)) static inline int FTYPE(copyEachArray)(TYPE** array, ATYPE* src,__arrayArg size) {
    //printf("    Copy each array\n");
    switch(size.len) {
        case 1:
            memcpy(array,src->pp,sizeof(TYPE*)*src->sz[0]);
            break;
        case 2:
            for(int i0=0;i0<src->sz[0];i0++){
                memcpy(array+size.sz[1]*i0,src->pp+src->sz[1]*i0,sizeof(TYPE*)*src->sz[1]);
            }
            break;
        case 3:
            for(int i1=0;i1<src->sz[1];i1++){
                for(int i0=0;i0<src->sz[0];i0++){
                    memcpy(array+(size.sz[1]*i0+i1)*size.sz[2],src->pp+(src->sz[1]*i0+i1)*src->sz[2],sizeof(TYPE*)*src->sz[2]);
                }
            }
            break;
        case 4:
            for(int i2=0;i2<src->sz[2];i2++){
                for(int i1=0;i1<src->sz[1];i1++){
                    for(int i0=0;i0<src->sz[0];i0++){
                        memcpy(array+((size.sz[1]*i0+i1)*size.sz[2]+i2)*size.sz[3],src->pp+((src->sz[1]*i0+i1)*src->sz[2]+i2)*src->sz[3],
                               sizeof(TYPE*)*src->sz[3]);
                    }
                }
            }
            break;
    }
    return TRUE;
}

__attribute__((always_inline)) static inline int FTYPE(copyEachStruct)(TYPE** array, ATYPE* src,__arrayArg size) {
    //printf("    Copy each struct\n");
    switch(size.len) {
        case 1:
            for(int x=0;x<src->sz[0];x++){
                if(src->pp[x]!=NULL)
                    FTYPE(copyStruct)(&array[x],src->pp[x]);
            }
            break;
        case 2:
            for(int i0=0;i0<src->sz[0];i0++){
                for(int x=0;x<src->sz[1];x++){
                    TYPE* p=src->pp[x+src->sz[1]*i0];
                    if(p!=NULL)
                        FTYPE(copyStruct)(&array[x]+size.sz[1]*i0,p);
                }
            }
            break;
        case 3:
            for(int i1=0;i1<src->sz[1];i1++){
                for(int i0=0;i0<src->sz[0];i0++){
                    for(int x=0;x<src->sz[2];x++){
                        TYPE* p=src->pp[x+(src->sz[1]*i0+i1)*src->sz[2] ];
                        if(p!=NULL)
                            FTYPE(copyStruct)(&array[x]+(size.sz[1]*i0+i1)*size.sz[2],p);
                    }
                }
            }
            break;
        case 4:
            for(int i2=0;i2<src->sz[2];i2++){
                for(int i1=0;i1<src->sz[1];i1++){
                    for(int i0=0;i0<src->sz[0];i0++){
                        for(int x=0;x<src->sz[3];x++){
                            TYPE* p=src->pp[x+((src->sz[1]*i0+i1)*src->sz[2]+i2)*src->sz[3]];
                            if(p!=NULL)
                                FTYPE(copyStruct)(&array[x]+((size.sz[1]*i0+i1)*size.sz[2]+i2)*size.sz[3],p);
                        }
                    }
                }
            }
            break;
    }
    return TRUE;
}

__attribute__((always_inline)) static inline int FTYPE(freeEachStruct)(ATYPE* src, int top) {
    //printf("    free each\n");
    switch(src->dim) {
        case 1:
            for(int x=0;x<src->sz[0];x++){
                FTYPE(freeStruct)(src->pp[x],top);
            }
            break;
        case 2:
            for(int i0=0;i0<src->sz[0];i0++){
                for(int x=0;x<src->sz[1];x++){
                    FTYPE(freeStruct)(src->pp[x+src->sz[1]*i0],top);
                }
            }
            break;
        case 3:
            for(int i1=0;i1<src->sz[1];i1++){
                for(int i0=0;i0<src->sz[0];i0++){
                    for(int x=0;x<src->sz[2];x++){
                        FTYPE(freeStruct)(src->pp[x+(src->sz[1]*i0+i1)*src->sz[2]],top);
                    }
                }
            }
            break;
        case 4:
            for(int i2=0;i2<src->sz[2];i2++){
                for(int i1=0;i1<src->sz[1];i1++){
                    for(int i0=0;i0<src->sz[0];i0++){
                        for(int x=0;x<src->sz[3];x++){
                            FTYPE(freeStruct)(src->pp[x+((src->sz[1]*i0+i1)*src->sz[2]+i2)*src->sz[3]],top);
                        }
                    }
                }
            }
            break;
    }
    return TRUE;
}

__attribute__((always_inline)) static inline int FTYPE(free)(ATYPE* src, int top) {
    if((src->flags & 0x17) == 0x6 && src->pp !=NULL) {
        FTYPE(freeEachStruct)(src,top);
        free(src->pp);
    }
    return TRUE;
}

__attribute__((always_inline)) static inline int FTYPE(reallocMoveArray)(ATYPE* dst, ATYPE* src,__arrayArg size) {
    int noCopy = TRUE;
    int shallow = FALSE;
    if(src==NULL || (src!=NULL && (src->flags & 0x18) == 0x08)) {
        //Don't need to keep old structs
        shallow = TRUE;
    }
    TYPE** array;
    if((dst->flags &0x2)!=0x2) {
        //Not allocated just alloc
        //printf("  Dst not allocated, just allocate\n");
        int sz = 1;
        for(int i=0;i<size.len;i++) {
            sz *= size.sz[i];
            dst->sz[i]=size.sz[i];
        }
        dst->pp=calloc(sizeof(TYPE*),sz);
        dst->flags = 0x6 | dst->flags;
        dst->dim = size.len;
        if(src!=NULL) {
            //Need to move data from src to dst
            noCopy = FALSE;
            array = dst->pp;
        }
    } else {
        if(dst->dim!=size.len) {
            //Ooops not same dimensions, something is wrong
            //Make it crash, since the generated code will not have bugs and it must be an external function using this in the wrong way
            //printf("  Ooops not same dimensions, something is wrong\n");
            dst->pp=NULL;
            return FALSE;
        }
        //check if need more memory
        //printf("  Check if need more memory\n");
        int szMax = 1;
        for(int i = 0; i<size.len; i++) {
            szMax *=size.sz[i];
            if(size.sz[i]>dst->sz[i]) {
                noCopy=FALSE;
            }
        }
        if(!noCopy) {
            //printf("  allocate memory\n");
            array = calloc(sizeof(TYPE*),szMax);
        }
        if(src!=NULL) {
            //Move data from src to dst instead of from dst to new dst
            noCopy = FALSE;
        } else {
            src = dst;
        }
    }
    if(!noCopy) {
        //Need to copy each old value list seperatly
        //printf("  Need to move each list seperately\n");
        if(shallow) {
            FTYPE(copyEachArray)(array, src, size);
        } else {
            FTYPE(copyEachStruct)(array, src, size);
        }
        if((dst->flags & 0x6)==0x6 && dst->pp!=array) {
            free(dst->pp);
        }
        dst->pp = array;
        dst->flags |= 0x6;
        memcpy(dst->sz,size.sz,sizeof(int32_t)*4);
    }
    return TRUE;
}

__attribute__((always_inline)) static inline int FTYPE(copy)(ATYPE* dst, ATYPE* src, __arrayArg dstIndex, __arrayArg srcIndex, __arrayArg srcMax) {
    if(dstIndex.len == 0 && srcIndex.len == 0 && (dst->dim == src->dim || dst->dim==0)) {
        //assign full variable dst from full variable src
        //we can just change ref when dst is full array & src allocated on heap and is a codegen var and not part of multi-dim.
        if((dst->flags & 0x10) == 0x0 && (src->flags & 0x1f) == 0xe) {
            //printf("Just move ref\n");
            //free old dst and redirect
            if((dst->flags & 0x6) == 0x6) {
                free(dst->pp);
            }
            dst->pp = src->pp;
            memcpy(dst->sz,src->sz,sizeof(int32_t)*4);
            dst->flags = (src->flags & 0x6) | (dst->flags & 0x8);
            src->flags = 0x0;
        } else {
            //need to copy data
            //printf("Copy data\n");
            FTYPE(reallocMoveArray)(dst,src,srcMax);
            //When src is codegen var on heap and not a part of multi-dim free it (the structs has already moved on)
            if((src->flags & 0x1f) == 0xe) {
                free(src->pp);
                src->flags = 0x0;
            }
        }
    } else {
        //partial array copying
        //printf("Partial array copy\n");
        if((dst->dim-dstIndex.len) != (src->dim-srcIndex.len)) {
            //Error not same dimensions
            //printf("Not same dimensions!!!\n");
            return FALSE;
        }
        //Make sure dst is big enough
        //printf("Make sure dst is big enough\n");
        FTYPE(reallocMoveArray)(dst, NULL, srcMax);
        //Create temporary partial arrays
        //printf("Create temp partial arrays\n");
        ATYPE tmpSrc;
        int index = 0;
        int i;
        for(i=0;i<dstIndex.len;i++) {
            index += dstIndex.sz[i];
            index *= srcMax.sz[i+1];
        }
        for(;i<srcMax.len-1;i++) {
            index *= srcMax.sz[i+1];
        }
        TYPE** array = dst->pp+index;
        __arrayArg partialSize;
        for(i=0;i<dst->dim-dstIndex.len;i++) {
            partialSize.sz[i]=srcMax.sz[dstIndex.len+i];
        }
        partialSize.len = dst->dim-dstIndex.len;
        index = 0;
        for(i=0;i<srcIndex.len;i++) {
            index += srcIndex.sz[i];
            index *= src->sz[i+1];
        }
        for(;i<src->dim-1;i++) {
            index *= src->sz[i+1];
        }
        tmpSrc.pp = src->pp+index;
        for(i=0;i<src->dim-srcIndex.len;i++) {
            tmpSrc.sz[i]=src->sz[srcIndex.len+i];
        }
        tmpSrc.dim = src->dim-srcIndex.len;
        tmpSrc.flags = 0x16;
        //and copy it
        //printf("Copy each struct\n");
        FTYPE(copyEachStruct)(array, &tmpSrc, partialSize);
        //When src is codegen var on heap and not a part of multi-dim free it
        if((src->flags & 0x1f) == 0xe) {
            //printf("Free old codegen\n");
            FTYPE(freeEachStruct)(src, TRUE);
            free(src->pp);
            src->flags = 0x0;
        }
    }
    //printf("copied dst sz[%i]:%i, %i, %i, %i\n",dst->dim,dst->sz[0],dst->sz[1],dst->sz[2],dst->sz[3]);
    return TRUE;
}
#endif

//Add string operation in addition to the char array functions
#if TYPE == char
#ifndef STRINGOPERATIONS
#define STRINGOPERATIONS

//concat 2 string variables op1 + op2 and store into dst, when dst==op1 set op1 to NULL
__attribute__((always_inline)) static inline int stringaddvv(ATYPE *dst, ATYPE *op1, ATYPE *op2) {
    if (op1 == NULL) {
        reallocMoveArraychar(dst, NULL,
                             maxArraySz(&(__arrayArg) {1, {dst->sz[0]}}, &(__arrayArg) {1, {op2->sz[0]}}, 0));
        strncat(dst->p, op2->p, op2->sz[0] - 1);
    } else {
        reallocMoveArraychar(dst, op1, maxArraySz(&(__arrayArg) {1, {op1->sz[0]}}, &(__arrayArg) {1, {op2->sz[0]}}, 0));
        strncat(dst->p, op2->p, op2->sz[0] - 1);
    }
    return TRUE;
}

//concat 1 string variables op1 with 1 literal string op2 and store into dst, when dst==op1 set op1 to NULL
__attribute__((always_inline)) static inline int stringaddvl(ATYPE *dst, ATYPE *op1, char *op2) {
    if (op1 == NULL) {
        int32_t l = (int32_t) strlen(op2);
        reallocMoveArraychar(dst, NULL, maxArraySz(&(__arrayArg) {1, {dst->sz[0]}}, &(__arrayArg) {1, {l + 1}}, 0));
        strncat(dst->p, op2, l);
    } else {
        int32_t l = (int32_t) strlen(op2);
        reallocMoveArraychar(dst, op1, maxArraySz(&(__arrayArg) {1, {op1->sz[0]}}, &(__arrayArg) {1, {l + 1}}, 0));
        strncat(dst->p, op2, l);
    }
    return TRUE;
}

//concat 1 literal string op1 with 1 string variables op2 and store into dst
__attribute__((always_inline)) static inline int stringaddlv(ATYPE *dst, char *op1, ATYPE *op2) {
    int32_t l = (int32_t) strlen(op1);
    reallocMoveArraychar(dst, NULL, maxArraySz(&(__arrayArg) {1, {l + 1}}, &(__arrayArg) {1, {op2->sz[0]}}, 0));
    strncpy(dst->p, op1, l);
    strncat(dst->p, op2->p, op2->sz[0] - 1);
    return TRUE;
}

//concat 2 literal strings op1 + op2 and store into dst
__attribute__((always_inline)) static inline int stringaddll(ATYPE *dst, char *op1, char *op2) {
    int32_t l1 = (int32_t) strlen(op1);
    int32_t l2 = (int32_t) strlen(op2);
    reallocMoveArraychar(dst, NULL, maxArraySz(&(__arrayArg) {1, {l1 + 1}}, &(__arrayArg) {1, {l2 + 1}}, 0));
    strncpy(dst->p, op1, l1);
    strncat(dst->p, op2, l2);
    return TRUE;
}

//assign 1 literal string op1 to dst
__attribute__((always_inline)) static inline int stringassignl(ATYPE *dst, char *op1) {
    int32_t l = (int32_t) strlen(op1);
    reallocMoveArraychar(dst, NULL, (__arrayArg) {1, {l + 1}});
    strncpy(dst->p, op1, l);
    return TRUE;
}

//are equal 2 string variables op1 to op2
__attribute__((always_inline)) static inline int stringeqvv(ATYPE *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1->p, op2->p);
    return eq == 0;
}

//are equal 1 string variable op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringeqvl(ATYPE *op1, char *op2) {
    int32_t eq = strcmp(op1->p, op2);
    return eq == 0;
}

//are equal 1 string literal op1 to 1 string variable op2
__attribute__((always_inline)) static inline int stringeqlv(char *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1, op2->p);
    return eq == 0;
}

//are equal 1 string variables op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringeqll(char *op1, char *op2) {
    int32_t eq = strcmp(op1, op2);
    return eq == 0;
}

//are not equal 2 string variables op1 to op2
__attribute__((always_inline)) static inline int stringnevv(ATYPE *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1->p, op2->p);
    return eq == 0;
}

//are not equal 1 string variable op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringnevl(ATYPE *op1, char *op2) {
    int32_t eq = strcmp(op1->p, op2);
    return eq == 0;
}

//are not equal 1 string literal op1 to 1 string variable op2
__attribute__((always_inline)) static inline int stringnelv(char *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1, op2->p);
    return eq == 0;
}

//are not equal 1 string variables op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringnell(char *op1, char *op2) {
    int32_t eq = strcmp(op1, op2);
    return eq == 0;
}

//are less than 2 string variables op1 to op2
__attribute__((always_inline)) static inline int stringltvv(ATYPE *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1->p, op2->p);
    return eq < 0;
}

//are less than 1 string variable op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringltvl(ATYPE *op1, char *op2) {
    int32_t eq = strcmp(op1->p, op2);
    return eq < 0;
}

//are less than 1 string literal op1 to 1 string variable op2
__attribute__((always_inline)) static inline int stringltlv(char *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1, op2->p);
    return eq < 0;
}

//are less than 1 string variables op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringltll(char *op1, char *op2) {
    int32_t eq = strcmp(op1, op2);
    return eq < 0;
}

//are greater than 2 string variables op1 to op2
__attribute__((always_inline)) static inline int stringgtvv(ATYPE *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1->p, op2->p);
    return eq > 0;
}

//are greater than 1 string variable op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringgtvl(ATYPE *op1, char *op2) {
    int32_t eq = strcmp(op1->p, op2);
    return eq > 0;
}

//are greater than 1 string literal op1 to 1 string variable op2
__attribute__((always_inline)) static inline int stringgtlv(char *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1, op2->p);
    return eq > 0;
}

//are greater than 1 string variables op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringgtll(char *op1, char *op2) {
    int32_t eq = strcmp(op1, op2);
    return eq > 0;
}

//are less than or equal 2 string variables op1 to op2
__attribute__((always_inline)) static inline int stringlevv(ATYPE *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1->p, op2->p);
    return eq <= 0;
}

//are less than or equal 1 string variable op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringlevl(ATYPE *op1, char *op2) {
    int32_t eq = strcmp(op1->p, op2);
    return eq <= 0;
}

//are less than or equal 1 string literal op1 to 1 string variable op2
__attribute__((always_inline)) static inline int stringlelv(char *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1, op2->p);
    return eq <= 0;
}

//are less than or equal 1 string variables op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringlell(char *op1, char *op2) {
    int32_t eq = strcmp(op1, op2);
    return eq <= 0;
}

//are greater than or equal 2 string variables op1 to op2
__attribute__((always_inline)) static inline int stringgevv(ATYPE *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1->p, op2->p);
    return eq >= 0;
}

//are greater than or equal 1 string variable op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringgevl(ATYPE *op1, char *op2) {
    int32_t eq = strcmp(op1->p, op2);
    return eq >= 0;
}

//are greater than or equal 1 string literal op1 to 1 string variable op2
__attribute__((always_inline)) static inline int stringgelv(char *op1, ATYPE *op2) {
    int32_t eq = strcmp(op1, op2->p);
    return eq >= 0;
}

//are greater than or equal 1 string variables op1 to 1 string literal op2
__attribute__((always_inline)) static inline int stringgell(char *op1, char *op2) {
    int32_t eq = strcmp(op1, op2);
    return eq >= 0;
}

#endif
#endif

#undef ATYPE_
#undef ATYPE__
#undef ATYPE
#undef FTYPE_
#undef FTYPE__
#undef FTYPE
#undef TYPE
